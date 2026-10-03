package br.ensaios.cliente.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.ensaios.cliente.data.LocalDb
import br.ensaios.cliente.data.LocalRecord
import br.ensaios.cliente.sync.SyncScheduler
import br.ensaios.shared.Perm
import br.ensaios.shared.RecordTypes
import br.ensaios.shared.UserInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""

@Composable
fun ClientesScreen(user: UserInfo, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val changes by LocalDb.changes.collectAsState()
    val list = remember(changes) {
        db.listByType(RecordTypes.CLIENTE).sortedBy { it.record.data.text("nome").lowercase() }
    }
    val canEdit = Perm.has(user, Perm.CADASTROS)
    var editing by remember { mutableStateOf<LocalRecord?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
            Text(
                "Clientes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (canEdit) Button(onClick = { creating = true }) { Text("Novo") }
        }
        if (list.isEmpty()) Text("Nenhum cliente cadastrado.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.record.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canEdit) { editing = item },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.record.data.text("nome"), fontWeight = FontWeight.SemiBold)
                        val contrato = item.record.data.text("contrato")
                        if (contrato.isNotEmpty()) Text("Contrato: $contrato")
                        if (item.pending) Text("Aguardando envio", color = Color(0xFFB26A00))
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val item = editing
        ClienteDialog(
            initial = item,
            onDismiss = { creating = false; editing = null },
            onSave = { nome, contrato ->
                val data = buildJsonObject {
                    put("nome", nome.trim())
                    put("contrato", contrato.trim())
                }
                db.saveLocal(RecordTypes.CLIENTE, item?.record?.id, data, user)
                SyncScheduler.syncSoon(context)
                creating = false
                editing = null
            },
            onDelete = if (item == null) null else {
                {
                    db.saveLocal(RecordTypes.CLIENTE, item.record.id, item.record.data, user, deleted = true)
                    SyncScheduler.syncSoon(context)
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun ClienteDialog(
    initial: LocalRecord?,
    onDismiss: () -> Unit,
    onSave: (nome: String, contrato: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var nome by remember { mutableStateOf(initial?.record?.data?.text("nome") ?: "") }
    var contrato by remember { mutableStateOf(initial?.record?.data?.text("contrato") ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo cliente" else "Editar cliente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it },
                    label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contrato, onValueChange = { contrato = it },
                    label = { Text("Contrato") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    Spacer(Modifier.padding(4.dp))
                    if (confirmDelete) {
                        TextButton(onClick = onDelete) {
                            Text("Confirmar exclusão", color = Color(0xFFC62828))
                        }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Excluir cliente", color = Color(0xFFC62828))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = nome.isNotBlank(), onClick = { onSave(nome, contrato) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
