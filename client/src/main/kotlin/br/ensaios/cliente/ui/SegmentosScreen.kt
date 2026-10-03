package br.ensaios.cliente.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.ensaios.cliente.data.LocalDb
import br.ensaios.cliente.data.Segmento
import br.ensaios.cliente.data.refNames
import br.ensaios.cliente.data.text
import br.ensaios.cliente.sync.SyncScheduler
import br.ensaios.shared.Km
import br.ensaios.shared.Num
import br.ensaios.shared.Perm
import br.ensaios.shared.RecordTypes
import br.ensaios.shared.UserInfo
import br.ensaios.shared.WriteRules
import java.time.LocalDate

private fun metros(m: Long): String = "$m m"
private fun area(a: Double): String = "${Num.fmt(a, 2)} m²"

/** Lista de segmentos agrupada por dia, com o total de extensão de cada dia. */
@Composable
fun SegmentosScreen(user: UserInfo, onBack: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val changes by LocalDb.changes.collectAsState()
    val segmentos = remember(changes) { db.listByType(RecordTypes.SEGMENTO).map { Segmento.from(it) } }
    val rodovias = remember(changes) { refNames(db, RecordTypes.RODOVIA) }
    val faixas = remember(changes) { refNames(db, RecordTypes.FAIXA) }
    val porDia = segmentos.groupBy { it.data }.toSortedMap(compareByDescending { it })

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("Segmentos", onBack) {
            if (Perm.has(user, Perm.CRIAR)) Button(onClick = onNew) { Text("Novo") }
        }
        if (segmentos.isEmpty()) Text("Nenhum segmento lançado.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            porDia.forEach { (dia, lista) ->
                item(key = "dia-$dia") {
                    val total = lista.sumOf { it.extensao ?: 0L }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(Num.date(dia), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${lista.size} segmento(s) · extensão total ${metros(total)}")
                        }
                    }
                }
                items(lista.sortedBy { it.kmIni ?: 0L }, key = { it.id ?: "" }) { s ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { s.id?.let(onOpen) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                listOf(rodovias[s.rodoviaId] ?: "", s.intervalo).filter { it.isNotEmpty() }.joinToString(" · "),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(listOf(faixas[s.faixaId] ?: "", s.lado, s.servico).filter { it.isNotEmpty() }.joinToString(" · "))
                            val ext = s.extensao
                            if (ext != null) {
                                Text("Extensão ${metros(ext)}" + (s.area?.let { " · área ${area(it)}" } ?: ""))
                            }
                            if (s.pending) Text("Aguardando envio", color = Color(0xFFB26A00))
                        }
                    }
                }
            }
        }
    }
}

/** Detalhes de um segmento e os ensaios dele. */
@Composable
fun SegmentoDetailScreen(user: UserInfo, id: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val changes by LocalDb.changes.collectAsState()
    val record = remember(changes, id) { db.get(id) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (record == null || record.record.deleted) {
        Column(Modifier.padding(16.dp)) {
            Header("Segmento", onBack)
            Text("Segmento não encontrado (pode ter sido excluído).")
        }
        return
    }
    val s = Segmento.from(record)
    val clientes = remember(changes) { refNames(db, RecordTypes.CLIENTE) }
    val rodovias = remember(changes) { refNames(db, RecordTypes.RODOVIA) }
    val faixas = remember(changes) { refNames(db, RecordTypes.FAIXA) }
    val canEdit = WriteRules.check(user, RecordTypes.SEGMENTO, false, record.record.createdBy, false) == null
    val canDelete = WriteRules.check(user, RecordTypes.SEGMENTO, false, record.record.createdBy, true) == null

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Header("Segmento", onBack) {
            if (canEdit) Button(onClick = onEdit) { Text("Editar") }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Info("Data", Num.date(s.data))
                Info("Cliente", clientes[s.clienteId] ?: "")
                Info("Rodovia", rodovias[s.rodoviaId] ?: "")
                Info("Serviço / camada", s.servico)
                Info("Posição", s.intervalo)
                Info("Lado", s.lado)
                Info("Faixa", faixas[s.faixaId] ?: "")
                Info("Largura da faixa", s.largura?.let { "${Num.plain(it)} m" } ?: "")
                Info("Extensão", s.extensao?.let { metros(it) } ?: "")
                Info("Área", s.area?.let { area(it) } ?: "")
                Info("Responsável", s.responsavel)
                Info("Observações", s.obs)
                if (s.pending) Text("Aguardando envio", color = Color(0xFFB26A00))
            }
        }
        Text("Ensaios deste segmento", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text("Os ensaios entram na versão 0.3.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (canDelete) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { confirmDelete = true }) { Text("Excluir segmento", color = Color(0xFFC62828)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir segmento") },
            text = { Text("O segmento vai para a lixeira.") },
            confirmButton = {
                TextButton(onClick = {
                    db.saveLocal(RecordTypes.SEGMENTO, id, record.record.data, user, deleted = true)
                    SyncScheduler.syncSoon(context)
                    confirmDelete = false
                    onBack()
                }) { Text("Excluir", color = Color(0xFFC62828)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Info(label: String, value: String) {
    if (value.isEmpty()) return
    Row {
        Text("$label: ", fontWeight = FontWeight.SemiBold)
        Text(value)
    }
}

/** Criação e edição de segmento. id = null cria um novo. */
@Composable
fun SegmentoEditScreen(user: UserInfo, id: String?, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val existing = remember(id) { id?.let { db.get(it) }?.let { Segmento.from(it) } }
    val clientes = remember { refNames(db, RecordTypes.CLIENTE) }
    val rodoviasAll = remember { db.listByType(RecordTypes.RODOVIA) }
    val faixas = remember { refNames(db, RecordTypes.FAIXA) }

    var data by remember { mutableStateOf(existing?.data ?: LocalDate.now().toString()) }
    var clienteId by remember { mutableStateOf(existing?.clienteId ?: "") }
    var rodoviaId by remember { mutableStateOf(existing?.rodoviaId ?: "") }
    var servico by remember { mutableStateOf(existing?.servico ?: "") }
    var kmIni by remember { mutableStateOf(existing?.kmIni?.let { Km.toDigits(it) } ?: "") }
    var kmFim by remember { mutableStateOf(existing?.kmFim?.let { Km.toDigits(it) } ?: "") }
    var lado by remember { mutableStateOf(existing?.lado ?: "") }
    var faixaId by remember { mutableStateOf(existing?.faixaId ?: "") }
    var largura by remember { mutableStateOf(existing?.largura?.let { Num.plain(it) } ?: "") }
    var responsavel by remember { mutableStateOf(existing?.responsavel ?: user.name) }
    var obs by remember { mutableStateOf(existing?.obs ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val rodoviaChoices = rodoviasAll
        .filter { clienteId.isEmpty() || it.record.data.text("cliente_id").let { c -> c.isEmpty() || c == clienteId } }
        .map { Choice(it.record.id, it.record.data.text("nome")) }
        .sortedBy { it.label.lowercase() }

    val preview = Segmento(
        id = id, data = data, clienteId = clienteId, rodoviaId = rodoviaId, servico = servico,
        kmIni = Km.fromDigits(kmIni), kmFim = Km.fromDigits(kmFim), lado = lado, faixaId = faixaId,
        largura = Num.parse(largura), responsavel = responsavel, obs = obs,
    )

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header(if (id == null) "Novo segmento" else "Editar segmento", { onDone(null) })
        DateInput("Data", data) { data = it }
        SelectInput("Cliente", clienteId, clientes.map { (k, v) -> Choice(k, v) }.sortedBy { it.label.lowercase() }) {
            clienteId = it
        }
        SelectInput("Rodovia *", rodoviaId, rodoviaChoices) { rodoviaId = it }
        TextInput("Serviço / camada", servico, { servico = it })
        KmInput("Posição inicial *", kmIni) { kmIni = it }
        KmInput("Posição final *", kmFim) { kmFim = it }
        SelectInput("Lado", lado, Segmento.LADOS.map { Choice(it, it) }) { lado = it }
        SelectInput("Faixa", faixaId, faixas.map { (k, v) -> Choice(k, v) }.sortedBy { it.label.lowercase() }) { faixaId = it }
        NumberInput("Largura da faixa", largura, { largura = it }, unit = "m")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Extensão: " + (preview.extensao?.let { metros(it) } ?: "—"), fontWeight = FontWeight.SemiBold)
                Text("Área: " + (preview.area?.let { area(it) } ?: "—"))
            }
        }

        TextInput("Responsável", responsavel, { responsavel = it })
        TextInput("Observações", obs, { obs = it }, singleLine = false)
        error?.let { Text(it, color = Color(0xFFC62828)) }
        Button(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = {
                error = when {
                    rodoviaId.isEmpty() -> "Escolha a rodovia"
                    preview.kmIni == null || preview.kmFim == null -> "Preencha a posição inicial e final"
                    largura.isNotBlank() && preview.largura == null -> "Largura inválida"
                    else -> null
                }
                if (error == null) {
                    val rodoviaNome = rodoviasAll.firstOrNull { it.record.id == rodoviaId }?.record?.data?.text("nome") ?: ""
                    val saved = db.saveLocal(
                        RecordTypes.SEGMENTO, id,
                        preview.toJson(rodoviaNome, faixas[faixaId] ?: ""),
                        user,
                    )
                    SyncScheduler.syncSoon(context)
                    onDone(saved)
                }
            },
        ) { Text("Salvar") }
        Spacer(Modifier.height(24.dp))
    }
}
