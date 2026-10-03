package br.ensaios.cliente.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.ensaios.cliente.data.AppSettings
import br.ensaios.cliente.data.Ensaios
import br.ensaios.cliente.data.LocalRecord
import br.ensaios.cliente.data.Segmento
import br.ensaios.cliente.data.formFrom
import br.ensaios.cliente.data.long
import br.ensaios.cliente.data.num
import br.ensaios.cliente.data.text
import br.ensaios.shared.Calc
import br.ensaios.shared.Km
import br.ensaios.shared.Lados
import br.ensaios.shared.Num
import br.ensaios.shared.RecordTypes
import br.ensaios.shared.TipoEnsaio
import br.ensaios.shared.UserInfo
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Campos de identificação que todo ensaio grava. */
private fun JsonObjectBuilder.cabecalho(st: EnsaioState, segmentoId: String, posM: Long?) {
    put("segmento_id", JsonPrimitive(segmentoId))
    put("serie", JsonPrimitive(st.serie))
    put("serie_num", JsonPrimitive(st.serieNum))
    put("ano", JsonPrimitive(st.ano))
    posM?.let { put("posicao", JsonPrimitive(it)) }
}

@Composable
private fun StatusPill(status: String) {
    when (status) {
        "ok" -> Pill("APROVADO", PillKind.OK)
        "bad" -> Pill("REPROVADO", PillKind.BAD)
        else -> MutedText("Sem limites cadastrados para o cliente deste segmento.")
    }
}

// =================================================================== taxa de aplicação

private val TIPOS_TAXA = listOf("Cimento", "Cal", "Agregado", "Imprimação")
private val taNumKeys = listOf("tara", "tara_material", "area", "dens_ligante", "teor_residuo", "espessura", "pct", "gs_lab")
private val taTextKeys = idTextKeys + listOf("tipo", "bandeja_id", "proctor_id", "proctor_serie")

@Composable
fun TaxaScreen(user: UserInfo, segmentoId: String, id: String?, onDone: () -> Unit) {
    val st = rememberEnsaio(TipoEnsaio.TAXA, user, segmentoId, id)
    val db = st.db
    val casas by AppSettings.casasTaxa.collectAsState()
    val seg = remember { db.get(segmentoId)?.let { Segmento.from(it) } }
    val v = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(formFrom(st.existing?.record?.data, taNumKeys, listOf("posicao", "posicao_fim"), taTextKeys))
            if (st.existing == null) {
                put("camada", seg?.servico ?: "")
                put("tipo", "Cimento")
                put("posicao", seg?.kmIni?.let { Km.toDigits(it) } ?: "")
                put("posicao_fim", seg?.kmFim?.let { Km.toDigits(it) } ?: "")
                // Espessura e % repetem a última taxa lançada.
                db.listByType(RecordTypes.TAXA).maxByOrNull { it.record.updatedAt }?.record?.data?.let { last ->
                    listOf("espessura", "pct", "dens_ligante", "teor_residuo").forEach { k -> last.num(k)?.let { put(k, Num.plain(it)) } }
                }
            }
        }
    }
    val lim = remember { st.existing?.record?.data?.limites() ?: limitesDoCliente(db, seg) }
    val bandejas = remember { db.listByType(RecordTypes.BANDEJA) }
    var proctors by remember { mutableStateOf(emptyList<LocalRecord>()) }
    var erro by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var draftSaved by remember { mutableStateOf(false) }
    var edits by remember { mutableIntStateOf(0) }
    val set: (String) -> (String) -> Unit = { k -> { x -> v[k] = x; edits++ } }

    val posM = Km.fromDigits(v["posicao"] ?: "")
    val posFim = Km.fromDigits(v["posicao_fim"] ?: "")
    val cimento = v["tipo"] == "Cimento"
    val imprimacao = v["tipo"] == "Imprimação"

    LaunchedEffect(posM, cimento) {
        if (!cimento) return@LaunchedEffect
        proctors = Ensaios.proctorsPara(db, segmentoId, posM)
        if ((v["proctor_id"] ?: "").isEmpty() && proctors.isNotEmpty() && st.canEdit) escolherProctor(v, proctors.first())
    }

    val r = Calc.taxa(
        v.n("tara"), v.n("tara_material"), v.n("area"),
        if (imprimacao) v.n("dens_ligante") else null, if (imprimacao) v.n("teor_residuo") else null,
        if (cimento) v.n("espessura") else null, if (cimento) v.n("gs_lab") else null, if (cimento) v.n("pct") else null,
    )
    val status = br.ensaios.cliente.data.statusDe(Calc.dentro(r.variacao, lim.num("taxa_var_min"), lim.num("taxa_var_max"), 1))

    fun buildData(): JsonObject = buildJsonObject {
        cabecalho(st, segmentoId, posM)
        posFim?.let { put("posicao_fim", JsonPrimitive(it)) }
        taTextKeys.forEach { k -> v[k]?.takeIf { it.isNotEmpty() }?.let { put(k, JsonPrimitive(it)) } }
        taNumKeys.forEach { k -> v.n(k)?.let { put(k, JsonPrimitive(it)) } }
        r.pesoMaterial?.let { put("material", JsonPrimitive(it)) }
        r.kgM2?.let { put("kg_m2", JsonPrimitive(it)) }
        r.lM2?.let { put("l_m2", JsonPrimitive(it)) }
        r.residual?.let { put("residual", JsonPrimitive(it)) }
        r.projeto?.let { put("projeto", JsonPrimitive(it)) }
        r.variacao?.let { put("variacao", JsonPrimitive(it)) }
        put("casas_taxa", JsonPrimitive(casas))
        put("status", JsonPrimitive(status))
        put("lim", lim)
        put("resumo", JsonPrimitive(listOfNotNull(st.serie, v["tipo"], r.kgM2?.let { "${Num.fmt(it, casas)} kg/m²" }).joinToString(" · ")))
    }

    LaunchedEffect(edits) {
        if (edits == 0 || !st.canEdit) return@LaunchedEffect
        delay(700)
        st.ensureSerie()
        st.save(buildData(), draft = true)
        draftSaved = true
    }

    val concluir: () -> Unit = {
        erro = when {
            posM == null -> "Preencha a posição inicial (km+m)"
            v.n("area") == null -> "Escolha a bandeja ou lona (precisa da área)"
            v.n("tara_material") == null -> "Preencha o peso da tara + material"
            r.kgM2 == null -> "Confira os pesos: não foi possível calcular a taxa"
            else -> null
        }
        if (erro == null) {
            st.ensureSerie()
            st.save(buildData(), draft = false)
            onDone()
        }
    }

    AppScreen(
        title = "Taxa de aplicação",
        icon = TipoEnsaio.TAXA.iconRes(),
        subtitle = listOf(st.serie.ifEmpty { "novo ensaio" }, seg?.intervalo ?: "").filter { it.isNotEmpty() }.joinToString(" · "),
        onBack = onDone,
    ) {
        SectionLabel("Identificação")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectInput("Tipo de taxa", v["tipo"] ?: "", TIPOS_TAXA.map { Choice(it, it) }, set("tipo"))
                TwoFields {
                    KmInput("Km inicial", v["posicao"] ?: "", set("posicao"), Modifier.weight(1f))
                    KmInput("Km final", v["posicao_fim"] ?: "", set("posicao_fim"), Modifier.weight(1f))
                }
                TwoFields {
                    SelectInput("Lado", v["lado"] ?: "", TipoEnsaio.TAXA.lados.map { Choice(it, Lados.label(it)) }, set("lado"), Modifier.weight(1f))
                    TextInput("Camada", v["camada"] ?: "", set("camada"), modifier = Modifier.weight(1f))
                }
            }
        }

        SectionLabel("Bandeja ou lona")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectInput(
                    "Bandeja / lona", v["bandeja_id"] ?: "",
                    bandejas.map { Choice(it.record.id, it.record.data.text("identificacao")) },
                    { bid ->
                        v["bandeja_id"] = bid
                        val b = bandejas.firstOrNull { it.record.id == bid }?.record?.data
                        v["area"] = b?.num("area")?.let { Num.plain(it) } ?: ""
                        v["tara"] = b?.num("tara")?.let { Num.plain(it) } ?: ""
                        edits++
                    },
                )
                TwoFields {
                    NumberInput("A · Tara", v["tara"] ?: "", set("tara"), "g", modifier = Modifier.weight(1f))
                    NumberInput("C · Tara + material", v["tara_material"] ?: "", set("tara_material"), "g", modifier = Modifier.weight(1f))
                }
                TwoFields {
                    CalcField("D · Peso do material (C−A)", r.pesoMaterial?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                    CalcField("Área", v.n("area")?.let { "${Num.plain(it)} m²" } ?: "", Modifier.weight(1f))
                }
                if (v.n("tara") == null) MutedText("Sem tara: conta como zero.")
            }
        }

        if (cimento) {
            SectionLabel("Taxa de projeto (Proctor)")
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectInput(
                        "Proctor", v["proctor_id"] ?: "",
                        proctors.map { p ->
                            val d = p.record.data
                            Choice(p.record.id, listOfNotNull(d.text("serie"), d.long("posicao")?.let { Km.format(it) }, d.num("gs")?.let { "γs ${Num.density(it)}" }).joinToString(" · "))
                        },
                        { pid -> proctors.firstOrNull { it.record.id == pid }?.let { escolherProctor(v, it) }; edits++ },
                    )
                    TwoFields {
                        NumberInput("Espessura da camada", v["espessura"] ?: "", set("espessura"), "cm", modifier = Modifier.weight(1f))
                        NumberInput("Cimento", v["pct"] ?: "", set("pct"), "%", modifier = Modifier.weight(1f))
                    }
                    CalcField("Massa esp. seca do Proctor", v.n("gs_lab")?.let { "${Num.density(it)} g/cm³" } ?: "")
                    MutedText("Projeto = 1 × 1 × espessura × γs ÷ 100 × % de cimento")
                }
            }
        }

        if (imprimacao) {
            SectionLabel("Ligante")
            AppCard {
                TwoFields {
                    NumberInput("Densidade do ligante", v["dens_ligante"] ?: "", set("dens_ligante"), "kg/L", modifier = Modifier.weight(1f))
                    NumberInput("Teor de resíduo", v["teor_residuo"] ?: "", set("teor_residuo"), "%", modifier = Modifier.weight(1f))
                }
            }
        }

        SectionLabel("Resultado")
        AppCard {
            val itens = mutableListOf("Taxa aplicada" to (r.kgM2?.let { "${Num.fmt(it, casas)} kg/m²" } ?: "—"))
            if (cimento) {
                itens.add("Projeto" to (r.projeto?.let { "${Num.fmt(it, casas)}" } ?: "—"))
                itens.add("Diferença" to (r.variacao?.let { "${Num.fmt(it, 1)}%" } ?: "—"))
            }
            if (imprimacao) {
                itens.add("L/m²" to (r.lM2?.let { Num.fmt(it, casas) } ?: "—"))
                itens.add("Residual" to (r.residual?.let { Num.fmt(it, casas) } ?: "—"))
            }
            ResultBox(itens)
            if (cimento) StatusPill(status)
        }

        TextInput("Observações", v["obs"] ?: "", set("obs"), singleLine = false)
        EnsaioFooter(st, draftSaved, erro, concluir) { askDelete = true }
    }

    if (askDelete) {
        ConfirmDialog("Excluir ensaio", "O ensaio ${st.serie} será excluído.", onDismiss = { askDelete = false }) {
            st.delete(); askDelete = false; onDone()
        }
    }
}

// =================================================================== umidade inicial para reciclagem

private val umNumKeys = listOf("u1", "u2", "u3")
private val umTextKeys = idTextKeys + listOf("capsula")

@Composable
fun UmidadeScreen(user: UserInfo, segmentoId: String, id: String?, onDone: () -> Unit) {
    val st = rememberEnsaio(TipoEnsaio.UMIDADE, user, segmentoId, id)
    val db = st.db
    val seg = remember { db.get(segmentoId)?.let { Segmento.from(it) } }
    val v = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(formFrom(st.existing?.record?.data, umNumKeys, listOf("posicao"), umTextKeys))
            if (st.existing == null) put("camada", seg?.servico ?: "")
        }
    }
    val lim = remember { st.existing?.record?.data?.limites() ?: limitesDoCliente(db, seg) }
    var erro by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var draftSaved by remember { mutableStateOf(false) }
    var edits by remember { mutableIntStateOf(0) }
    val set: (String) -> (String) -> Unit = { k -> { x -> v[k] = x; edits++ } }

    val posM = Km.fromDigits(v["posicao"] ?: "")
    val um = Calc.umidade(v.n("u1"), v.n("u2"), v.n("u3"))
    val status = br.ensaios.cliente.data.statusDe(Calc.dentro(um.h, lim.num("umid_inicial_min"), lim.num("umid_inicial_max"), 1))

    fun buildData(): JsonObject = buildJsonObject {
        cabecalho(st, segmentoId, posM)
        umTextKeys.forEach { k -> v[k]?.takeIf { it.isNotEmpty() }?.let { put(k, JsonPrimitive(it)) } }
        umNumKeys.forEach { k -> v.n(k)?.let { put(k, JsonPrimitive(it)) } }
        um.h?.let { put("h", JsonPrimitive(it)) }
        put("status", JsonPrimitive(status))
        put("lim", lim)
        put("resumo", JsonPrimitive(listOfNotNull(st.serie, posM?.let { Km.format(it) }, um.h?.let { "h ${Num.fmt(it, 1)}%" }).joinToString(" · ")))
    }

    LaunchedEffect(edits) {
        if (edits == 0 || !st.canEdit) return@LaunchedEffect
        delay(700)
        st.ensureSerie()
        st.save(buildData(), draft = true)
        draftSaved = true
    }

    val concluir: () -> Unit = {
        erro = when {
            posM == null -> "Preencha a posição (km+m)"
            listOf("u1", "u2").any { v.n(it) == null } -> "Preencha os pesos úmido e seco"
            um.h == null -> "Confira os pesos: não foi possível calcular a umidade"
            else -> null
        }
        if (erro == null) {
            st.ensureSerie()
            st.save(buildData(), draft = false)
            onDone()
        }
    }

    AppScreen(
        title = "Umidade inicial",
        icon = TipoEnsaio.UMIDADE.iconRes(),
        subtitle = listOf(st.serie.ifEmpty { "novo ensaio" }, seg?.intervalo ?: "").filter { it.isNotEmpty() }.joinToString(" · "),
        onBack = onDone,
    ) {
        SectionLabel("Identificação")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TwoFields {
                    KmInput("Posição", v["posicao"] ?: "", set("posicao"), Modifier.weight(1f))
                    SelectInput("Lado", v["lado"] ?: "", TipoEnsaio.UMIDADE.lados.map { Choice(it, Lados.label(it)) }, set("lado"), Modifier.weight(1f))
                }
                TextInput("Camada", v["camada"] ?: "", set("camada"))
            }
        }
        SectionLabel("Umidade (cápsula)")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("Cápsula nº", v["capsula"] ?: "", set("capsula"))
                TwoFields {
                    NumberInput("U1 · Cápsula + solo úmido", v["u1"] ?: "", set("u1"), "g", modifier = Modifier.weight(1f))
                    NumberInput("U2 · Cápsula + solo seco", v["u2"] ?: "", set("u2"), "g", modifier = Modifier.weight(1f))
                }
                NumberInput("U3 · Tara da cápsula (vazio = 0)", v["u3"] ?: "", set("u3"), "g")
                TwoFields {
                    CalcField("U4 · Água", um.u4Agua?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                    CalcField("U5 · Solo seco", um.u5SoloSeco?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                }
            }
        }
        SectionLabel("Resultado")
        AppCard {
            ResultBox(listOf("Umidade" to (um.h?.let { "${Num.fmt(it, 1)}%" } ?: "—")))
            StatusPill(status)
            val limTxt = listOfNotNull(
                lim.num("umid_inicial_min")?.let { "mín ${Num.plain(it)}%" },
                lim.num("umid_inicial_max")?.let { "máx ${Num.plain(it)}%" },
            )
            if (limTxt.isNotEmpty()) MutedText("Limites: " + limTxt.joinToString(" · "))
        }
        TextInput("Observações", v["obs"] ?: "", set("obs"), singleLine = false)
        EnsaioFooter(st, draftSaved, erro, concluir) { askDelete = true }
    }

    if (askDelete) {
        ConfirmDialog("Excluir ensaio", "O ensaio ${st.serie} será excluído.", onDismiss = { askDelete = false }) {
            st.delete(); askDelete = false; onDone()
        }
    }
}

// =================================================================== resíduo por evaporação (RR-2C)

private val reNumKeys = listOf("carga", "recipiente", "com_amostra", "com_residuo")
private val reTextKeys = idTextKeys + listOf("placa", "veiculo_id", "emulsao")

@Composable
fun ResiduoScreen(user: UserInfo, segmentoId: String, id: String?, onDone: () -> Unit) {
    val st = rememberEnsaio(TipoEnsaio.RESIDUO, user, segmentoId, id)
    val db = st.db
    val seg = remember { db.get(segmentoId)?.let { Segmento.from(it) } }
    val v = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(formFrom(st.existing?.record?.data, reNumKeys, listOf("posicao"), reTextKeys))
            if (st.existing == null) {
                put("emulsao", "RR-2C")
                put("lado", "Inteiro")
            }
        }
    }
    val lim = remember { st.existing?.record?.data?.limites() ?: limitesDoCliente(db, seg) }
    val veiculos = remember { db.listByType(RecordTypes.VEICULO) }
    var erro by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var draftSaved by remember { mutableStateOf(false) }
    var edits by remember { mutableIntStateOf(0) }
    val set: (String) -> (String) -> Unit = { k -> { x -> v[k] = x; edits++ } }

    val posM = Km.fromDigits(v["posicao"] ?: "")
    val res = Calc.residuo(v.n("recipiente"), v.n("com_amostra"), v.n("com_residuo"))
    val status = br.ensaios.cliente.data.statusDe(Calc.dentro(res, lim.num("residuo_min"), null, 1))

    fun buildData(): JsonObject = buildJsonObject {
        cabecalho(st, segmentoId, posM)
        reTextKeys.forEach { k -> v[k]?.takeIf { it.isNotEmpty() }?.let { put(k, JsonPrimitive(it)) } }
        reNumKeys.forEach { k -> v.n(k)?.let { put(k, JsonPrimitive(it)) } }
        res?.let { put("residuo", JsonPrimitive(it)) }
        put("status", JsonPrimitive(status))
        put("lim", lim)
        put("resumo", JsonPrimitive(listOfNotNull(st.serie, v["placa"], res?.let { "${Num.fmt(it, 1)}%" }).joinToString(" · ")))
    }

    LaunchedEffect(edits) {
        if (edits == 0 || !st.canEdit) return@LaunchedEffect
        delay(700)
        st.ensureSerie()
        st.save(buildData(), draft = true)
        draftSaved = true
    }

    val concluir: () -> Unit = {
        erro = when {
            (v["placa"] ?: "").isBlank() -> "Informe a placa do veículo"
            v.n("com_amostra") == null || v.n("com_residuo") == null -> "Preencha os pesos com amostra e com resíduo"
            res == null -> "Confira os pesos: não foi possível calcular o resíduo"
            else -> null
        }
        if (erro == null) {
            st.ensureSerie()
            st.save(buildData(), draft = false)
            onDone()
        }
    }

    AppScreen(
        title = "Resíduo da emulsão",
        icon = TipoEnsaio.RESIDUO.iconRes(),
        subtitle = listOf(st.serie.ifEmpty { "novo ensaio" }, seg?.intervalo ?: "").filter { it.isNotEmpty() }.joinToString(" · "),
        onBack = onDone,
    ) {
        SectionLabel("Carga")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (veiculos.isNotEmpty()) {
                    SelectInput(
                        "Veículo do cadastro", v["veiculo_id"] ?: "",
                        veiculos.map { Choice(it.record.id, it.record.data.text("placa")) },
                        { vid ->
                            v["veiculo_id"] = vid
                            veiculos.firstOrNull { it.record.id == vid }?.let { v["placa"] = it.record.data.text("placa") }
                            edits++
                        },
                    )
                }
                TwoFields {
                    TextInput("Placa", v["placa"] ?: "", set("placa"), modifier = Modifier.weight(1f))
                    NumberInput("Peso da carga", v["carga"] ?: "", set("carga"), "t", modifier = Modifier.weight(1f))
                }
                TextInput("Emulsão", v["emulsao"] ?: "", set("emulsao"))
            }
        }
        SectionLabel("Onde foi aplicada")
        AppCard {
            TwoFields {
                KmInput("Posição", v["posicao"] ?: "", set("posicao"), Modifier.weight(1f))
                SelectInput("Lado", v["lado"] ?: "", TipoEnsaio.RESIDUO.lados.map { Choice(it, Lados.label(it)) }, set("lado"), Modifier.weight(1f))
            }
        }
        SectionLabel("Resíduo por evaporação (NBR 14376)")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput("Recipiente (vazio = 0)", v["recipiente"] ?: "", set("recipiente"), "g")
                TwoFields {
                    NumberInput("Recipiente + amostra", v["com_amostra"] ?: "", set("com_amostra"), "g", modifier = Modifier.weight(1f))
                    NumberInput("Recipiente + resíduo", v["com_residuo"] ?: "", set("com_residuo"), "g", modifier = Modifier.weight(1f))
                }
            }
        }
        SectionLabel("Resultado")
        AppCard {
            ResultBox(listOf("Resíduo" to (res?.let { "${Num.fmt(it, 1)}%" } ?: "—")))
            StatusPill(status)
            lim.num("residuo_min")?.let { MutedText("Mínimo do cliente: ${Num.plain(it)}%") }
        }
        TextInput("Observações", v["obs"] ?: "", set("obs"), singleLine = false)
        EnsaioFooter(st, draftSaved, erro, concluir) { askDelete = true }
    }

    if (askDelete) {
        ConfirmDialog("Excluir ensaio", "O ensaio ${st.serie} será excluído.", onDismiss = { askDelete = false }) {
            st.delete(); askDelete = false; onDone()
        }
    }
}
