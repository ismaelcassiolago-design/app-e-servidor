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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import br.ensaios.cliente.data.AppSettings
import br.ensaios.cliente.data.Ensaios
import br.ensaios.cliente.data.LocalDb
import br.ensaios.cliente.data.LocalRecord
import br.ensaios.cliente.data.Segmento
import br.ensaios.cliente.data.formFrom
import br.ensaios.cliente.data.long
import br.ensaios.cliente.data.num
import br.ensaios.cliente.data.statusInSitu
import br.ensaios.cliente.data.text
import br.ensaios.cliente.sync.SyncScheduler
import br.ensaios.cliente.ui.theme.appColors
import br.ensaios.shared.Calc
import br.ensaios.shared.Km
import br.ensaios.shared.Num
import br.ensaios.shared.RecordTypes
import br.ensaios.shared.Serie
import br.ensaios.shared.TipoEnsaio
import br.ensaios.shared.UserInfo
import br.ensaios.shared.WriteRules
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.util.UUID

// =================================================================== base comum dos ensaios

/** Estado comum de um formulário de ensaio: identificação, série, rascunho automático. */
private class EnsaioState(
    val db: LocalDb,
    val tipo: TipoEnsaio,
    val user: UserInfo,
    val segmentoId: String,
    val id: String,
    val existing: LocalRecord?,
    val appContext: android.content.Context,
) {
    var serieNum: Int = existing?.record?.data?.long("serie_num")?.toInt() ?: 0
    var ano: Int = existing?.record?.data?.long("ano")?.toInt() ?: LocalDate.now().year
    var serie: String = existing?.record?.data?.text("serie") ?: ""

    val canEdit: Boolean = WriteRules.check(user, tipo.type, existing == null, existing?.record?.createdBy, false) == null
    val canDelete: Boolean = existing != null &&
        (existing.draft && existing.record.seq == 0L || WriteRules.check(user, tipo.type, false, existing.record.createdBy, true) == null)

    /** Reserva o número de série na primeira vez que o ensaio é salvo. */
    fun ensureSerie() {
        if (serieNum == 0) {
            serieNum = db.nextSerialNumber(tipo.type, user.id)
            ano = LocalDate.now().year
            serie = Serie.format(tipo.prefixo, ano, user.number, serieNum)
        }
    }

    fun save(data: JsonObject, draft: Boolean) {
        db.saveLocal(tipo.type, id, data, user, draft = draft)
        if (!draft) SyncScheduler.syncSoon(appContext)
    }

    fun delete() {
        val e = existing ?: return
        if (e.draft && e.record.seq == 0L) {
            db.revert(id, null) // rascunho nunca enviado: apaga só do celular
        } else {
            db.saveLocal(tipo.type, id, e.record.data, user, deleted = true)
            SyncScheduler.syncSoon(appContext)
        }
    }
}

@Composable
private fun rememberEnsaio(tipo: TipoEnsaio, user: UserInfo, segmentoId: String, id: String?): EnsaioState {
    val context = LocalContext.current
    return remember(id) {
        val db = LocalDb.get(context)
        val realId = id ?: UUID.randomUUID().toString()
        EnsaioState(db, tipo, user, segmentoId, realId, id?.let { db.get(it) }, context.applicationContext)
    }
}

/** Campos comuns a todos os ensaios (identificação). */
private val idTextKeys = listOf("lado", "camada", "obs")

private fun JsonObject.limites(): JsonObject? = this["lim"] as? JsonObject

/** Parâmetros do cliente do segmento (copiados para o ensaio no momento do lançamento). */
private fun limitesDoCliente(db: LocalDb, seg: Segmento?): JsonObject {
    val cliente = seg?.clienteId?.takeIf { it.isNotEmpty() }?.let { db.get(it) }?.record?.data
    return buildJsonObject {
        listOf("gc_min", "gc_max", "umid_desvio_min", "umid_desvio_max").forEach { k ->
            cliente?.num(k)?.let { put(k, JsonPrimitive(it)) }
        }
    }
}

private fun SnapshotStateMap<String, String>.n(key: String): Double? = Num.parse(this[key])

/** Rodapé com botões de concluir e excluir, e a situação do ensaio. */
@Composable
private fun EnsaioFooter(
    st: EnsaioState,
    draftSaved: Boolean,
    erro: String?,
    onConcluir: () -> Unit,
    onExcluir: () -> Unit,
) {
    val c = appColors
    erro?.let { Text(it, color = c.bad, fontWeight = FontWeight.SemiBold) }
    if (!st.canEdit) {
        MutedText("Você pode consultar este ensaio, mas não alterar.")
        return
    }
    MutedText(if (draftSaved) "Rascunho salvo automaticamente neste celular." else "Tudo que você digita é salvo automaticamente.")
    PrimaryButton("Concluir e enviar", onClick = onConcluir)
    if (st.canDelete) SecondaryButton("Excluir ensaio", color = c.bad, onClick = onExcluir)
}

// =================================================================== in situ (frasco de areia)

private val isNumKeys = listOf("l1", "l2", "l4", "l6", "l8", "l9", "u1", "u2", "u3", "gs_lab", "h_ot")
private val isTextKeys = idTextKeys + listOf("furo", "capsula", "frasco_id", "proctor_id", "proctor_serie")

@Composable
fun InSituScreen(user: UserInfo, segmentoId: String, id: String?, onDone: () -> Unit) {
    val st = rememberEnsaio(TipoEnsaio.IN_SITU, user, segmentoId, id)
    val db = st.db
    val casasGc by AppSettings.casasGc.collectAsState()
    val seg = remember { db.get(segmentoId)?.let { Segmento.from(it) } }
    val v = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(formFrom(st.existing?.record?.data, isNumKeys, listOf("posicao"), isTextKeys))
            if (st.existing == null) put("camada", seg?.servico ?: "")
        }
    }
    val lim = remember { st.existing?.record?.data?.limites() ?: limitesDoCliente(db, seg) }
    val frascos = remember { db.listByType(RecordTypes.FRASCO) }
    var proctors by remember { mutableStateOf(emptyList<LocalRecord>()) }
    var erro by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var draftSaved by remember { mutableStateOf(false) }
    var edits by remember { mutableIntStateOf(0) }
    val set: (String) -> (String) -> Unit = { k -> { x -> v[k] = x; edits++ } }

    val posM = Km.fromDigits(v["posicao"] ?: "")
    // Lista de Proctors: do segmento, o mais próximo primeiro; escolhe sozinho se ainda não houver.
    LaunchedEffect(posM) {
        proctors = Ensaios.proctorsPara(db, segmentoId, posM)
        if ((v["proctor_id"] ?: "").isEmpty() && proctors.isNotEmpty() && st.canEdit) {
            escolherProctor(v, proctors.first())
        }
    }

    val r = Calc.inSitu(
        v.n("l1"), v.n("l2"), v.n("l4"), v.n("l6"), v.n("l8"), v.n("l9"),
        v.n("u1"), v.n("u2"), v.n("u3"), v.n("gs_lab"), v.n("h_ot"),
    )
    val status = statusInSitu(r.gc, r.umidade.h, v.n("h_ot"), lim, casasGc)

    fun buildData(): JsonObject = buildJsonObject {
        put("segmento_id", JsonPrimitive(segmentoId))
        put("serie", JsonPrimitive(st.serie))
        put("serie_num", JsonPrimitive(st.serieNum))
        put("ano", JsonPrimitive(st.ano))
        posM?.let { put("posicao", JsonPrimitive(it)) }
        isTextKeys.forEach { k -> v[k]?.takeIf { it.isNotEmpty() }?.let { put(k, JsonPrimitive(it)) } }
        isNumKeys.forEach { k -> v.n(k)?.let { put(k, JsonPrimitive(it)) } }
        r.l7Volume?.let { put("volume", JsonPrimitive(it)) }
        r.l10PesoSolo?.let { put("peso_solo", JsonPrimitive(it)) }
        r.umidade.h?.let { put("h", JsonPrimitive(it)) }
        r.gh?.let { put("gh", JsonPrimitive(it)) }
        r.gs?.let { put("gs", JsonPrimitive(it)) }
        r.gc?.let { put("gc", JsonPrimitive(it)) }
        put("casas_gc", JsonPrimitive(casasGc))
        put("status", JsonPrimitive(status))
        put("lim", lim)
        put("resumo", JsonPrimitive(listOfNotNull(st.serie, posM?.let { Km.format(it) }, r.gc?.let { "GC ${Num.fmt(it, casasGc)}%" }).joinToString(" · ")))
    }

    // Rascunho automático: salva 0,7 s depois da última alteração.
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
            v.n("l4") == null || v.n("l6") == null -> "Escolha o frasco"
            listOf("l1", "l2", "l8", "l9").any { v.n(it) == null } -> "Preencha os pesos de 1, 2, 8 e 9"
            listOf("u1", "u2", "u3").any { v.n(it) == null } -> "Preencha a umidade (U1, U2 e U3)"
            r.gs == null -> "Confira os pesos: não foi possível calcular a massa específica"
            else -> null
        }
        if (erro == null) {
            st.ensureSerie()
            st.save(buildData(), draft = false)
            onDone()
        }
    }

    val c = appColors
    AppScreen(
        title = "In situ",
        icon = TipoEnsaio.IN_SITU.iconRes(),
        subtitle = listOf(st.serie.ifEmpty { "novo ensaio" }, seg?.intervalo ?: "").filter { it.isNotEmpty() }.joinToString(" · "),
        onBack = onDone,
    ) {
        SectionLabel("Identificação")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TwoFields {
                    KmInput("Posição", v["posicao"] ?: "", set("posicao"), Modifier.weight(1f))
                    TextInput("Furo", v["furo"] ?: "", set("furo"), modifier = Modifier.weight(1f))
                }
                TwoFields {
                    SelectInput("Lado", v["lado"] ?: "", TipoEnsaio.IN_SITU.lados.map { Choice(it, it) }, set("lado"), Modifier.weight(1f))
                    TextInput("Camada", v["camada"] ?: "", set("camada"), modifier = Modifier.weight(1f))
                }
            }
        }

        SectionLabel("Densidade (DNER-ME 092/94)")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectInput(
                    "Frasco", v["frasco_id"] ?: "",
                    frascos.map { Choice(it.record.id, it.record.data.text("identificacao")) },
                    { fid ->
                        v["frasco_id"] = fid
                        val f = frascos.firstOrNull { it.record.id == fid }?.record?.data
                        v["l4"] = f?.num("peso_funil_placa")?.let { Num.plain(it) } ?: ""
                        v["l6"] = f?.num("massa_esp_areia")?.let { Num.plain(it) } ?: ""
                        edits++
                    },
                )
                TwoFields {
                    NumberInput("1 · Frasco antes", v["l1"] ?: "", set("l1"), "g", modifier = Modifier.weight(1f))
                    NumberInput("2 · Frasco depois", v["l2"] ?: "", set("l2"), "g", modifier = Modifier.weight(1f))
                }
                TwoFields {
                    CalcField("3 · Areia deslocada (1−2)", r.l3AreiaDeslocada?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                    CalcField("4 · Areia funil e placa", v.n("l4")?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                }
                TwoFields {
                    CalcField("5 · Areia na cavidade (3−4)", r.l5AreiaCavidade?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                    CalcField("6 · Massa esp. areia", v.n("l6")?.let { "${Num.density(it)} g/cm³" } ?: "", Modifier.weight(1f))
                }
                CalcField("7 · Volume do solo (5/6)", r.l7Volume?.let { "${Num.fmt(it, 0)} cm³" } ?: "")
                TwoFields {
                    NumberInput("8 · Solo + recipiente", v["l8"] ?: "", set("l8"), "g", modifier = Modifier.weight(1f))
                    NumberInput("9 · Recipiente", v["l9"] ?: "", set("l9"), "g", modifier = Modifier.weight(1f))
                }
                CalcField("10 · Peso do solo (8−9)", r.l10PesoSolo?.let { "${Num.mass(it)} g" } ?: "")
            }
        }

        SectionLabel("Umidade de campo (cápsula)")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("Cápsula nº", v["capsula"] ?: "", set("capsula"))
                TwoFields {
                    NumberInput("U1 · Cápsula + solo úmido", v["u1"] ?: "", set("u1"), "g", modifier = Modifier.weight(1f))
                    NumberInput("U3 · Cápsula", v["u3"] ?: "", set("u3"), "g", modifier = Modifier.weight(1f))
                }
                val hOt = v.n("h_ot")
                val alvo = r.u2Alvo
                if (alvo != null && hOt != null) {
                    val alvoH = hOt - 2
                    AppCard(background = c.warnSoft, border = c.warn) {
                        Text("Alvo para U2 (umidade ${Num.fmt(alvoH, 1)}%)", color = c.warn, fontWeight = FontWeight.Bold)
                        Text("${Num.fmt(alvo, 2)} g", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        val u2 = v.n("u2")
                        if (u2 != null) {
                            MutedText(
                                if (u2 > alvo) "Balança acima do alvo: umidade ainda acima de ${Num.fmt(alvoH, 1)}%."
                                else "Balança no alvo ou abaixo: umidade em ${Num.fmt(alvoH, 1)}% ou menos."
                            )
                        }
                    }
                } else if (hOt == null) {
                    MutedText("O alvo de U2 aparece quando houver um Proctor de referência.")
                }
                NumberInput("U2 · Cápsula + solo seco", v["u2"] ?: "", set("u2"), "g")
                TwoFields {
                    CalcField("U4 · Água", r.umidade.u4Agua?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                    CalcField("U5 · Solo seco", r.umidade.u5SoloSeco?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                }
                CalcField("U6 · Umidade", r.umidade.h?.let { "${Num.fmt(it, 1)} %" } ?: "")
            }
        }

        SectionLabel("Proctor de referência")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectInput(
                    "Proctor", v["proctor_id"] ?: "",
                    proctors.map { p ->
                        val d = p.record.data
                        Choice(
                            p.record.id,
                            listOfNotNull(
                                d.text("serie"),
                                d.long("posicao")?.let { Km.format(it) },
                                d.num("gs")?.let { "γs ${Num.density(it)}" },
                                d.num("h")?.let { "h ${Num.fmt(it, 1)}%" },
                            ).joinToString(" · "),
                        )
                    },
                    { pid ->
                        val p = proctors.firstOrNull { it.record.id == pid }
                        if (p != null) escolherProctor(v, p) else {
                            v["proctor_id"] = ""; v["gs_lab"] = ""; v["h_ot"] = ""; v["proctor_serie"] = ""
                        }
                        edits++
                    },
                )
                if (proctors.isEmpty()) MutedText("Nenhum Proctor lançado ainda. Lance o Proctor no segmento para calcular o grau de compactação.")
                TwoFields {
                    CalcField("Massa esp. seca máx.", v.n("gs_lab")?.let { "${Num.density(it)} g/cm³" } ?: "", Modifier.weight(1f))
                    CalcField("Umidade ótima", v.n("h_ot")?.let { "${Num.fmt(it, 1)} %" } ?: "", Modifier.weight(1f))
                }
            }
        }

        SectionLabel("Resultado")
        AppCard {
            ResultBox(
                listOf(
                    "γh" to (r.gh?.let { Num.density(it) } ?: "—"),
                    "γs" to (r.gs?.let { Num.density(it) } ?: "—"),
                    "GC" to (r.gc?.let { "${Num.fmt(it, casasGc)}%" } ?: "—"),
                )
            )
            when (status) {
                "ok" -> Pill("APROVADO", PillKind.OK)
                "bad" -> Pill("REPROVADO", PillKind.BAD)
                else -> MutedText("Sem limites cadastrados para o cliente deste segmento.")
            }
            val limTxt = listOfNotNull(
                lim.num("gc_min")?.let { "GC mín ${Num.plain(it)}%" },
                lim.num("gc_max")?.let { "GC máx ${Num.plain(it)}%" },
                lim.num("umid_desvio_min")?.let { "h − h_ót ≥ ${Num.plain(it)}" },
                lim.num("umid_desvio_max")?.let { "h − h_ót ≤ ${Num.plain(it)}" },
            )
            if (limTxt.isNotEmpty()) MutedText("Limites: " + limTxt.joinToString(" · "))
        }

        TextInput("Observações", v["obs"] ?: "", set("obs"), singleLine = false)
        EnsaioFooter(st, draftSaved, erro, concluir) { askDelete = true }
    }

    if (askDelete) {
        ConfirmDialog("Excluir ensaio", "O ensaio ${st.serie} será excluído.", onDismiss = { askDelete = false }) {
            st.delete()
            askDelete = false
            onDone()
        }
    }
}

private fun escolherProctor(v: SnapshotStateMap<String, String>, p: LocalRecord) {
    val d = p.record.data
    v["proctor_id"] = p.record.id
    v["proctor_serie"] = d.text("serie")
    v["gs_lab"] = d.num("gs")?.let { Num.plain(it) } ?: ""
    v["h_ot"] = d.num("h")?.let { Num.plain(it) } ?: ""
}

// =================================================================== Proctor de um ponto

private val prNumKeys = listOf("u1", "u2", "u3", "bruto", "cil_massa", "cil_volume")
private val prTextKeys = idTextKeys + listOf("capsula", "cilindro_id", "energia", "camadas", "golpes", "soquete")

@Composable
fun ProctorScreen(user: UserInfo, segmentoId: String, id: String?, onDone: () -> Unit) {
    val st = rememberEnsaio(TipoEnsaio.PROCTOR, user, segmentoId, id)
    val db = st.db
    val seg = remember { db.get(segmentoId)?.let { Segmento.from(it) } }
    val v = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(formFrom(st.existing?.record?.data, prNumKeys, listOf("posicao"), prTextKeys))
            if (st.existing == null) {
                put("camada", seg?.servico ?: "")
                // Energia, camadas, golpes e soquete: repete o último Proctor lançado.
                db.listByType(RecordTypes.PROCTOR).maxByOrNull { it.record.updatedAt }?.record?.data?.let { last ->
                    listOf("energia", "camadas", "golpes", "soquete").forEach { k -> put(k, last.text(k)) }
                }
            }
        }
    }
    val cilindros = remember { db.listByType(RecordTypes.CILINDRO) }
    var erro by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var draftSaved by remember { mutableStateOf(false) }
    var edits by remember { mutableIntStateOf(0) }
    val set: (String) -> (String) -> Unit = { k -> { x -> v[k] = x; edits++ } }

    val posM = Km.fromDigits(v["posicao"] ?: "")
    val r = Calc.proctor(v.n("u1"), v.n("u2"), v.n("u3"), v.n("bruto"), v.n("cil_massa"), v.n("cil_volume"))

    fun buildData(): JsonObject = buildJsonObject {
        put("segmento_id", JsonPrimitive(segmentoId))
        put("serie", JsonPrimitive(st.serie))
        put("serie_num", JsonPrimitive(st.serieNum))
        put("ano", JsonPrimitive(st.ano))
        posM?.let { put("posicao", JsonPrimitive(it)) }
        prTextKeys.forEach { k -> v[k]?.takeIf { it.isNotEmpty() }?.let { put(k, JsonPrimitive(it)) } }
        prNumKeys.forEach { k -> v.n(k)?.let { put(k, JsonPrimitive(it)) } }
        r.soloUmido?.let { put("solo_umido", JsonPrimitive(it)) }
        r.umidade.h?.let { put("h", JsonPrimitive(it)) }
        r.gh?.let { put("gh", JsonPrimitive(it)) }
        r.gs?.let { put("gs", JsonPrimitive(it)) }
        put("status", JsonPrimitive("ref"))
        put("resumo", JsonPrimitive(listOfNotNull(st.serie, posM?.let { Km.format(it) }, r.gs?.let { "γs ${Num.density(it)}" }).joinToString(" · ")))
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
            v.n("cil_massa") == null || v.n("cil_volume") == null -> "Escolha o cilindro"
            v.n("bruto") == null -> "Preencha o peso bruto úmido"
            listOf("u1", "u2", "u3").any { v.n(it) == null } -> "Preencha a umidade (U1, U2 e U3)"
            r.gs == null -> "Confira os pesos: não foi possível calcular a massa específica"
            else -> null
        }
        if (erro == null) {
            st.ensureSerie()
            st.save(buildData(), draft = false)
            onDone()
        }
    }

    AppScreen(
        title = "Proctor",
        icon = TipoEnsaio.PROCTOR.iconRes(),
        subtitle = listOf(st.serie.ifEmpty { "novo ensaio" }, seg?.intervalo ?: "").filter { it.isNotEmpty() }.joinToString(" · "),
        onBack = onDone,
    ) {
        SectionLabel("Identificação")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TwoFields {
                    KmInput("Posição", v["posicao"] ?: "", set("posicao"), Modifier.weight(1f))
                    SelectInput("Lado", v["lado"] ?: "", TipoEnsaio.PROCTOR.lados.map { Choice(it, it) }, set("lado"), Modifier.weight(1f))
                }
                TextInput("Camada", v["camada"] ?: "", set("camada"))
                TwoFields {
                    TextInput("Energia", v["energia"] ?: "", set("energia"), modifier = Modifier.weight(1f))
                    TextInput("Soquete", v["soquete"] ?: "", set("soquete"), modifier = Modifier.weight(1f))
                }
                TwoFields {
                    NumberInput("Nº de camadas", v["camadas"] ?: "", set("camadas"), integer = true, modifier = Modifier.weight(1f))
                    NumberInput("Nº de golpes", v["golpes"] ?: "", set("golpes"), integer = true, modifier = Modifier.weight(1f))
                }
            }
        }

        SectionLabel("Umidade (cápsula)")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("Cápsula nº", v["capsula"] ?: "", set("capsula"))
                TwoFields {
                    NumberInput("Peso bruto úmido", v["u1"] ?: "", set("u1"), "g", modifier = Modifier.weight(1f))
                    NumberInput("Peso bruto seco", v["u2"] ?: "", set("u2"), "g", modifier = Modifier.weight(1f))
                }
                NumberInput("Peso da cápsula", v["u3"] ?: "", set("u3"), "g")
                TwoFields {
                    CalcField("Peso da água", r.umidade.u4Agua?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                    CalcField("Peso do solo seco", r.umidade.u5SoloSeco?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                }
                CalcField("Umidade", r.umidade.h?.let { "${Num.fmt(it, 1)} %" } ?: "")
            }
        }

        SectionLabel("Cilindro")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectInput(
                    "Cilindro", v["cilindro_id"] ?: "",
                    cilindros.map { Choice(it.record.id, it.record.data.text("identificacao")) },
                    { cid ->
                        v["cilindro_id"] = cid
                        val d = cilindros.firstOrNull { it.record.id == cid }?.record?.data
                        v["cil_massa"] = d?.num("massa")?.let { Num.plain(it) } ?: ""
                        v["cil_volume"] = d?.num("volume")?.let { Num.plain(it) } ?: ""
                        edits++
                    },
                )
                TwoFields {
                    CalcField("Peso do cilindro", v.n("cil_massa")?.let { "${Num.mass(it)} g" } ?: "", Modifier.weight(1f))
                    CalcField("Volume do cilindro", v.n("cil_volume")?.let { "${Num.plain(it)} cm³" } ?: "", Modifier.weight(1f))
                }
                NumberInput("Peso bruto úmido (cilindro + solo)", v["bruto"] ?: "", set("bruto"), "g")
                CalcField("Peso do solo úmido", r.soloUmido?.let { "${Num.mass(it)} g" } ?: "")
            }
        }

        SectionLabel("Resultado")
        AppCard {
            ResultBox(
                listOf(
                    "γh" to (r.gh?.let { Num.density(it) } ?: "—"),
                    "γs máx" to (r.gs?.let { Num.density(it) } ?: "—"),
                    "h ót" to (r.umidade.h?.let { "${Num.fmt(it, 1)}%" } ?: "—"),
                )
            )
            MutedText("Proctor de um ponto: a massa específica seca e a umidade do ponto são o resultado.")
        }

        TextInput("Observações", v["obs"] ?: "", set("obs"), singleLine = false)
        EnsaioFooter(st, draftSaved, erro, concluir) { askDelete = true }
    }

    if (askDelete) {
        ConfirmDialog("Excluir ensaio", "O ensaio ${st.serie} será excluído.", onDismiss = { askDelete = false }) {
            st.delete()
            askDelete = false
            onDone()
        }
    }
}
