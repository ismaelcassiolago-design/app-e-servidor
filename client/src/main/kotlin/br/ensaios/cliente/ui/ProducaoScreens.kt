package br.ensaios.cliente.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ensaios.cliente.data.AppSettings
import br.ensaios.cliente.data.Dia
import br.ensaios.cliente.data.Ensaios
import br.ensaios.cliente.data.LocalDb
import br.ensaios.cliente.data.Mes
import br.ensaios.cliente.data.Producao
import br.ensaios.cliente.data.Segmento
import br.ensaios.cliente.data.mesesDoAno
import br.ensaios.cliente.data.nomeMes
import br.ensaios.cliente.data.refNames
import br.ensaios.cliente.data.text
import br.ensaios.cliente.sync.SyncScheduler
import br.ensaios.cliente.ui.theme.appColors
import br.ensaios.shared.Km
import br.ensaios.shared.Num
import br.ensaios.shared.Perm
import br.ensaios.shared.RecordTypes
import br.ensaios.shared.TipoEnsaio
import br.ensaios.shared.UserInfo
import br.ensaios.shared.WriteRules
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private fun metros(m: Long): String = "$m m"
private fun area(a: Double): String = "${Num.fmt(a, 2)} m²"

// =================================================================== meses

/** Lista de meses de produção (adicionados à mão). */
@Composable
fun MesesScreen(user: UserInfo, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val changes by LocalDb.changes.collectAsState()
    val meses = remember(changes) { Producao.meses(db) }
    val segs = remember(changes) { Producao.todosSegmentos(db) }
    var adding by remember { mutableStateOf(false) }
    val canCreate = Perm.has(user, Perm.CRIAR)

    val semDia = remember(changes) { Producao.semDia(db) }

    AppScreen(title = "Dias de produção", subtitle = "Escolha o mês", fab = if (canCreate) ({ adding = true }) else null) {
        if (semDia.isNotEmpty()) {
            AppCard(border = appColors.warn, background = appColors.warnSoft) {
                TitleText("${semDia.size} segmento(s) sem dia de produção")
                MutedText(
                    "Lançados antes da organização por mês e dia: " +
                        semDia.mapNotNull { it.data.takeIf { d -> d.isNotEmpty() }?.let { d -> Num.date(d) } }.distinct().joinToString(", ") +
                        ". O app cria o mês e o dia de cada um e coloca os segmentos dentro."
                )
                if (canCreate) {
                    PrimaryButton("Organizar automaticamente") {
                        Producao.organizar(db, user)
                        SyncScheduler.syncSoon(context)
                    }
                }
            }
        }
        if (meses.isEmpty()) EmptyText("Nenhum mês ainda. Toque em + para adicionar o mês de produção.")
        meses.forEach { m ->
            val dias = Producao.dias(db, m)
            val total = dias.sumOf { d -> Producao.segmentos(db, d, segs).sumOf { it.extensao ?: 0L } }
            AppCard(onClick = { onOpen(m.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(m.ym.substringAfter("-"), m.ym.substringBefore("-"))
                    Column(Modifier.padding(start = 12.dp)) {
                        TitleText(nomeMes(m.ym))
                        MutedText("${dias.size} dia(s) de produção · ${metros(total)}")
                        if (m.pending) Pill("Aguardando envio", PillKind.WARN)
                    }
                }
            }
        }
    }

    if (adding) {
        val hoje = LocalDate.now()
        var mes by remember { mutableStateOf(hoje.monthValue.toString()) }
        var ano by remember { mutableStateOf(hoje.year.toString()) }
        var erro by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Adicionar mês") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectInput("Mês", mes, mesesDoAno().mapIndexed { i, n -> Choice((i + 1).toString(), n) }, { mes = it })
                    SelectInput("Ano", ano, (hoje.year - 1..hoje.year + 1).map { Choice(it.toString(), it.toString()) }, { ano = it })
                    erro?.let { Text(it, color = appColors.bad) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ym = "%s-%02d".format(ano, mes.toIntOrNull() ?: 1)
                    if (meses.any { it.ym == ym }) {
                        erro = "Esse mês já existe."
                    } else {
                        db.saveLocal(RecordTypes.MES, null, buildJsonObject {
                            put("mes", JsonPrimitive(ym))
                            put("resumo", JsonPrimitive(nomeMes(ym)))
                        }, user)
                        SyncScheduler.syncSoon(context)
                        adding = false
                    }
                }) { Text("Adicionar") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancelar") } },
        )
    }
}

// =================================================================== dias do mês

@Composable
fun MesScreen(user: UserInfo, mesId: String, onBack: () -> Unit, onOpenDia: (String) -> Unit) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val changes by LocalDb.changes.collectAsState()
    val mes = remember(changes, mesId) { Producao.mes(db, mesId) }
    if (mes == null) {
        AppScreen(title = "Mês", onBack = onBack) { EmptyText("Mês não encontrado (pode ter sido excluído).") }
        return
    }
    val dias = remember(changes, mesId) { Producao.dias(db, mes) }
    val segs = remember(changes) { Producao.todosSegmentos(db) }
    val canCreate = Perm.has(user, Perm.CRIAR)
    var erro by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    val totalMes = dias.sumOf { d -> Producao.segmentos(db, d, segs).sumOf { it.extensao ?: 0L } }

    val addDia: () -> Unit = {
        val ym = YearMonth.parse(mes.ym)
        val hoje = LocalDate.now()
        val inicial = if (YearMonth.from(hoje) == ym) hoje else ym.atDay(1)
        val dlg = DatePickerDialog(context, { _, y, m, d ->
            val data = LocalDate.of(y, m + 1, d).toString()
            if (dias.any { it.data == data }) {
                erro = "O dia ${Num.date(data)} já existe."
            } else {
                erro = null
                db.saveLocal(RecordTypes.DIA, null, buildJsonObject {
                    put("data", JsonPrimitive(data))
                    put("mes_id", JsonPrimitive(mes.id))
                    put("resumo", JsonPrimitive(Num.date(data)))
                }, user)
                SyncScheduler.syncSoon(context)
            }
        }, inicial.year, inicial.monthValue - 1, inicial.dayOfMonth)
        val zone = ZoneId.systemDefault()
        dlg.datePicker.minDate = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        dlg.datePicker.maxDate = ym.atEndOfMonth().atStartOfDay(zone).toInstant().toEpochMilli()
        dlg.show()
    }

    AppScreen(
        title = nomeMes(mes.ym),
        subtitle = "${dias.size} dia(s) · ${metros(totalMes)}",
        onBack = onBack,
        fab = if (canCreate) addDia else null,
    ) {
        erro?.let { Text(it, color = appColors.bad) }
        if (dias.isEmpty()) {
            EmptyText("Nenhum dia ainda. Toque em + para adicionar um dia de produção.")
            if (Perm.has(user, Perm.EXCLUIR)) SecondaryButton("Excluir este mês", color = appColors.bad) { askDelete = true }
        }
        dias.forEach { d ->
            val ss = Producao.segmentos(db, d, segs)
            val total = ss.sumOf { it.extensao ?: 0L }
            val dt = LocalDate.parse(d.data)
            AppCard(onClick = { onOpenDia(d.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge("%02d".format(dt.dayOfMonth), diaSemana(dt))
                    Column(Modifier.padding(start = 12.dp)) {
                        TitleText(Num.date(d.data))
                        MutedText("${ss.size} segmento(s) · ${metros(total)}")
                        if (d.pending) Pill("Aguardando envio", PillKind.WARN)
                    }
                }
            }
        }
    }

    if (askDelete) {
        ConfirmDialog("Excluir mês", "O mês ${nomeMes(mes.ym)} vai para a lixeira.", onDismiss = { askDelete = false }) {
            db.get(mes.id)?.let { db.saveLocal(RecordTypes.MES, mes.id, it.record.data, user, deleted = true) }
            SyncScheduler.syncSoon(context)
            askDelete = false
            onBack()
        }
    }
}

private fun diaSemana(d: LocalDate): String =
    listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")[d.dayOfWeek.value - 1]

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Excluir", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = appColors.bad) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// =================================================================== segmentos do dia

@Composable
fun DiaScreen(user: UserInfo, diaId: String, onBack: () -> Unit, onOpenSegmento: (String) -> Unit, onNewSegmento: () -> Unit) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val changes by LocalDb.changes.collectAsState()
    val dia = remember(changes, diaId) { Producao.dia(db, diaId) }
    if (dia == null) {
        AppScreen(title = "Dia", onBack = onBack) { EmptyText("Dia não encontrado (pode ter sido excluído).") }
        return
    }
    val casasGc by AppSettings.casasGc.collectAsState()
    val segs = remember(changes, diaId) { Producao.segmentos(db, dia) }
    val rodovias = remember(changes) { refNames(db, RecordTypes.RODOVIA) }
    val faixas = remember(changes) { refNames(db, RecordTypes.FAIXA) }
    val pistas = remember(changes) { refNames(db, RecordTypes.PISTA) }
    val total = segs.sumOf { it.extensao ?: 0L }
    val areaTotal = segs.sumOf { it.area ?: 0.0 }
    var askDelete by remember { mutableStateOf(false) }

    AppScreen(
        title = Num.date(dia.data),
        subtitle = "${segs.size} segmento(s) · ${metros(total)}",
        onBack = onBack,
        fab = if (Perm.has(user, Perm.CRIAR)) onNewSegmento else null,
    ) {
        if (segs.isNotEmpty()) {
            ResultBox(listOf("Extensão do dia" to metros(total), "Área do dia" to area(areaTotal)))
        }
        if (segs.isEmpty()) {
            EmptyText("Nenhum segmento neste dia. Toque em + para lançar.")
            if (Perm.has(user, Perm.EXCLUIR)) SecondaryButton("Excluir este dia", color = appColors.bad) { askDelete = true }
        }
        segs.forEach { s ->
            val ens = s.id?.let { Ensaios.doSegmento(db, it, casasGc) } ?: emptyList()
            SegmentoCard(s, rodovias, pistas, faixas, ens.count { it.status == "ok" }, ens.count { it.status == "bad" }, ens.size) {
                s.id?.let(onOpenSegmento)
            }
        }
    }

    if (askDelete) {
        ConfirmDialog("Excluir dia", "O dia ${Num.date(dia.data)} vai para a lixeira.", onDismiss = { askDelete = false }) {
            db.get(dia.id)?.let { db.saveLocal(RecordTypes.DIA, dia.id, it.record.data, user, deleted = true) }
            SyncScheduler.syncSoon(context)
            askDelete = false
            onBack()
        }
    }
}

@Composable
private fun SegmentoCard(
    s: Segmento, rodovias: Map<String, String>, pistas: Map<String, String>, faixas: Map<String, String>,
    ok: Int, bad: Int, totalEnsaios: Int, onClick: () -> Unit,
) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(s.kmIni?.let { Km.format(it) } ?: "—", s.kmFim?.let { Km.format(it) } ?: "—")
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TitleText(listOf(s.extensao?.let { metros(it) } ?: "", pistas[s.pistaId] ?: "", faixas[s.faixaId] ?: "").filter { it.isNotEmpty() }.joinToString(" · "))
                MutedText(listOf(rodovias[s.rodoviaId] ?: "", s.servico, s.area?.let { area(it) } ?: "").filter { it.isNotEmpty() }.joinToString(" · "))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ok > 0) Pill("$ok ✓", PillKind.OK)
                    if (bad > 0) Pill("$bad ✗", PillKind.BAD)
                    if (totalEnsaios == 0) Pill("sem ensaios", PillKind.NEUTRAL)
                    if (s.pending) Pill("Aguardando envio", PillKind.WARN)
                }
            }
        }
    }
}

// =================================================================== segmento e seus ensaios

@Composable
fun SegmentoDetailScreen(
    user: UserInfo, id: String, onBack: () -> Unit, onEdit: () -> Unit,
    onOpenEnsaio: (TipoEnsaio, String) -> Unit, onNewEnsaio: (TipoEnsaio) -> Unit,
) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val changes by LocalDb.changes.collectAsState()
    val casasGc by AppSettings.casasGc.collectAsState()
    val record = remember(changes, id) { db.get(id) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (record == null || record.record.deleted) {
        AppScreen(title = "Segmento", onBack = onBack) { EmptyText("Segmento não encontrado (pode ter sido excluído).") }
        return
    }
    val s = Segmento.from(record)
    val clientes = remember(changes) { refNames(db, RecordTypes.CLIENTE) }
    val rodovias = remember(changes) { refNames(db, RecordTypes.RODOVIA) }
    val faixas = remember(changes) { refNames(db, RecordTypes.FAIXA) }
    val pistas = remember(changes) { refNames(db, RecordTypes.PISTA) }
    val ensaios = remember(changes, id, casasGc) { Ensaios.doSegmento(db, id, casasGc) }
    val canEdit = WriteRules.check(user, RecordTypes.SEGMENTO, false, record.record.createdBy, false) == null
    val canDelete = WriteRules.check(user, RecordTypes.SEGMENTO, false, record.record.createdBy, true) == null

    AppScreen(
        title = s.intervalo.ifEmpty { "Segmento" },
        subtitle = listOf(Num.date(s.data), rodovias[s.rodoviaId] ?: "", pistas[s.pistaId] ?: "", faixas[s.faixaId] ?: "", s.extensao?.let { metros(it) } ?: "")
            .filter { it.isNotEmpty() }.joinToString(" · "),
        onBack = onBack,
        actions = { if (canEdit) HeaderAction("Editar", onEdit) },
    ) {
        SectionLabel("Ensaios deste segmento")
        if (ensaios.isEmpty()) EmptyText("Nenhum ensaio ainda.")
        ensaios.forEach { e ->
            val kind = when (e.status) {
                "ok" -> PillKind.OK
                "bad" -> PillKind.BAD
                else -> PillKind.PRIMARY
            }
            AppCard(onClick = { onOpenEnsaio(e.tipo, e.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EnsaioBadge(painterResource(e.tipo.iconRes()), e.posicao.ifEmpty { "—" }, kind)
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TitleText(e.titulo)
                        MutedText(listOf(e.serie, e.detalhe).filter { it.isNotEmpty() }.joinToString(" · "))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            when {
                                e.draft -> Pill("RASCUNHO", PillKind.WARN)
                                e.status == "ok" -> Pill("APROVADO", PillKind.OK)
                                e.status == "bad" -> Pill("REPROVADO", PillKind.BAD)
                                e.status == "ref" -> Pill("REFERÊNCIA", PillKind.PRIMARY)
                            }
                            if (e.pending) Pill("Aguardando envio", PillKind.WARN)
                        }
                    }
                }
            }
        }

        if (Perm.has(user, Perm.CRIAR)) {
            AppCard(border = appColors.primary, background = appColors.primarySoft) {
                Text("+ Novo ensaio", color = appColors.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TipoEnsaio.values().toList().chunked(2).forEach { par ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        par.forEach { t ->
                            Tile(
                                painterResource(t.iconRes()), t.nome,
                                if (t.disponivel) "toque para lançar" else "em breve",
                                modifier = Modifier.weight(1f),
                            ) { if (t.disponivel) onNewEnsaio(t) }
                        }
                        if (par.size == 1) Column(Modifier.weight(1f)) {}
                    }
                }
            }
        }

        SectionLabel("Dados do segmento")
        AppCard {
            InfoRow("Cliente", clientes[s.clienteId] ?: "")
            InfoRow("Rodovia", rodovias[s.rodoviaId] ?: "")
            InfoRow("Serviço / camada", s.servico)
            InfoRow("Posição", s.intervalo)
            InfoRow("Pista", pistas[s.pistaId] ?: "")
            InfoRow("Faixa", faixas[s.faixaId] ?: "")
            InfoRow("Largura da faixa", s.largura?.let { "${Num.plain(it)} m" } ?: "")
            InfoRow("Extensão", s.extensao?.let { metros(it) } ?: "")
            InfoRow("Área", s.area?.let { area(it) } ?: "")
            InfoRow("Responsável", s.responsavel)
            InfoRow("Observações", s.obs)
            if (s.pending) Pill("Aguardando envio", PillKind.WARN)
        }
        if (canDelete) SecondaryButton("Excluir segmento", color = appColors.bad) { confirmDelete = true }
    }

    if (confirmDelete) {
        ConfirmDialog("Excluir segmento", "O segmento vai para a lixeira.", onDismiss = { confirmDelete = false }) {
            db.saveLocal(RecordTypes.SEGMENTO, id, record.record.data, user, deleted = true)
            SyncScheduler.syncSoon(context)
            confirmDelete = false
            onBack()
        }
    }
}

// =================================================================== novo / editar segmento

/** Criação (id = null, dentro de um dia) e edição de segmento. */
@Composable
fun SegmentoEditScreen(user: UserInfo, id: String?, diaId: String?, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val existing = remember(id) { id?.let { db.get(it) }?.let { Segmento.from(it) } }
    val dia: Dia? = remember(diaId) { (diaId ?: existing?.diaId)?.takeIf { it.isNotEmpty() }?.let { Producao.dia(db, it) } }
    val clientes = remember { refNames(db, RecordTypes.CLIENTE) }
    val rodoviasAll = remember { db.listByType(RecordTypes.RODOVIA) }
    val faixas = remember { refNames(db, RecordTypes.FAIXA) }
    val pistas = remember { refNames(db, RecordTypes.PISTA) }
    // Ao criar, sugere cliente, rodovia, serviço e faixa do último segmento lançado.
    val ultimo = remember { if (id == null) Producao.todosSegmentos(db).maxByOrNull { it.data } else null }
    val base = existing ?: ultimo

    var clienteId by remember { mutableStateOf(base?.clienteId ?: "") }
    var rodoviaId by remember { mutableStateOf(base?.rodoviaId ?: "") }
    var servico by remember { mutableStateOf(base?.servico ?: "") }
    var kmIni by remember { mutableStateOf(existing?.kmIni?.let { Km.toDigits(it) } ?: ultimo?.kmFim?.let { Km.toDigits(it) } ?: "") }
    var kmFim by remember { mutableStateOf(existing?.kmFim?.let { Km.toDigits(it) } ?: "") }
    var pistaId by remember { mutableStateOf(base?.pistaId ?: "") }
    var faixaId by remember { mutableStateOf(base?.faixaId ?: "") }
    var largura by remember { mutableStateOf(base?.largura?.let { Num.plain(it) } ?: "") }
    var responsavel by remember { mutableStateOf(existing?.responsavel ?: user.name) }
    var obs by remember { mutableStateOf(existing?.obs ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val data = dia?.data ?: existing?.data ?: LocalDate.now().toString()
    val rodoviaChoices = rodoviasAll
        .filter { clienteId.isEmpty() || it.record.data.text("cliente_id").let { c -> c.isEmpty() || c == clienteId } }
        .map { Choice(it.record.id, it.record.data.text("nome")) }
        .sortedBy { it.label.lowercase() }

    val preview = Segmento(
        id = id, diaId = dia?.id ?: existing?.diaId ?: "", data = data, clienteId = clienteId, rodoviaId = rodoviaId,
        servico = servico, kmIni = Km.fromDigits(kmIni), kmFim = Km.fromDigits(kmFim), pistaId = pistaId, faixaId = faixaId,
        largura = Num.parse(largura), responsavel = responsavel, obs = obs,
    )

    val salvar: () -> Unit = {
        error = when {
            rodoviaId.isEmpty() -> "Escolha a rodovia"
            preview.kmIni == null || preview.kmFim == null -> "Preencha a posição inicial e final"
            largura.isNotBlank() && preview.largura == null -> "Largura inválida"
            else -> null
        }
        if (error == null) {
            val rodoviaNome = rodoviasAll.firstOrNull { it.record.id == rodoviaId }?.record?.data?.text("nome") ?: ""
            val saved = db.saveLocal(RecordTypes.SEGMENTO, id, preview.toJson(rodoviaNome, faixas[faixaId] ?: ""), user)
            SyncScheduler.syncSoon(context)
            onDone(saved)
        }
    }

    AppScreen(
        title = if (id == null) "Novo segmento" else "Editar segmento",
        subtitle = Num.date(data),
        onBack = { onDone(null) },
        actions = { HeaderAction("Salvar", salvar) },
    ) {
        SectionLabel("Onde")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectInput("Cliente", clienteId, clientes.map { (k, v) -> Choice(k, v) }.sortedBy { it.label.lowercase() }, { clienteId = it })
                SelectInput("Rodovia *", rodoviaId, rodoviaChoices, { rodoviaId = it })
                TextInput("Serviço / camada", servico, { servico = it })
            }
        }
        SectionLabel("Posição")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TwoFields {
                    KmInput("Início *", kmIni, { kmIni = it }, Modifier.weight(1f))
                    KmInput("Fim *", kmFim, { kmFim = it }, Modifier.weight(1f))
                }
                TwoFields {
                    SelectInput("Pista", pistaId, pistas.map { (k, v) -> Choice(k, v) }.sortedBy { it.label.lowercase() }, { pistaId = it }, Modifier.weight(1f))
                    SelectInput("Faixa ou acostamento", faixaId, faixas.map { (k, v) -> Choice(k, v) }.sortedBy { it.label.lowercase() }, { faixaId = it }, Modifier.weight(1f))
                }
                NumberInput("Largura da faixa", largura, { largura = it }, unit = "m")
                ResultBox(listOf("Extensão" to (preview.extensao?.let { metros(it) } ?: "—"), "Área" to (preview.area?.let { area(it) } ?: "—")))
            }
        }
        SectionLabel("Responsável")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("Responsável", responsavel, { responsavel = it })
                TextInput("Observações", obs, { obs = it }, singleLine = false)
            }
        }
        error?.let { Text(it, color = appColors.bad, fontWeight = FontWeight.SemiBold) }
        PrimaryButton("Salvar segmento", onClick = salvar)
    }
}
