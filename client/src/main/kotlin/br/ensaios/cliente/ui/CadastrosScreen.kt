package br.ensaios.cliente.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import br.ensaios.cliente.data.buildData
import br.ensaios.cliente.data.display
import br.ensaios.cliente.data.formValues
import br.ensaios.cliente.data.refNames
import br.ensaios.cliente.data.text
import br.ensaios.cliente.data.validateForm
import br.ensaios.cliente.sync.SyncScheduler
import br.ensaios.shared.CadastroSpec
import br.ensaios.shared.Cadastros
import br.ensaios.shared.FieldKind
import br.ensaios.shared.Perm
import br.ensaios.shared.UserInfo

/** Tela com a lista de todos os cadastros. */
@Composable
fun CadastrosMenuScreen(onBack: () -> Unit, onOpen: (CadastroSpec) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header("Cadastros", onBack)
        Cadastros.all.forEach { spec ->
            OutlinedButton(onClick = { onOpen(spec) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(spec.title, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun Header(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

/** Lista e edição de um cadastro qualquer, montadas a partir da definição em Cadastros. */
@Composable
fun CadastroListScreen(spec: CadastroSpec, user: UserInfo, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val changes by LocalDb.changes.collectAsState()
    val list = remember(changes, spec) {
        db.listByType(spec.type).sortedBy { it.record.data.text(spec.titleKey).lowercase() }
    }
    val refs = remember(changes, spec) {
        spec.fields.mapNotNull { it.refType }.distinct().associateWith { refNames(db, it) }
    }
    val canEdit = Perm.has(user, Perm.CADASTROS)
    var editing by remember { mutableStateOf<LocalRecord?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(spec.title, onBack) {
            if (canEdit) Button(onClick = { creating = true }) { Text("Novo") }
        }
        if (!canEdit) Text("Você pode consultar, mas não alterar os cadastros.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (list.isEmpty()) Text("Nada cadastrado.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.record.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth().clickable(enabled = canEdit) { editing = item }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.record.data.text(spec.titleKey), fontWeight = FontWeight.SemiBold)
                        spec.fields
                            .filter { it.key != spec.titleKey && it.section.isEmpty() }
                            .take(3)
                            .forEach { f ->
                                val v = item.record.data.display(f, refs[f.refType] ?: emptyMap())
                                if (v.isNotEmpty()) Text("${f.label}: $v")
                            }
                        if (item.pending) Text("Aguardando envio", color = Color(0xFFB26A00))
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val item = editing
        CadastroDialog(
            spec = spec,
            initial = item,
            refs = refs,
            onDismiss = { creating = false; editing = null },
            onSave = { values ->
                db.saveLocal(spec.type, item?.record?.id, buildData(spec, values), user)
                SyncScheduler.syncSoon(context)
                creating = false
                editing = null
            },
            onDelete = if (item == null) null else {
                {
                    db.saveLocal(spec.type, item.record.id, item.record.data, user, deleted = true)
                    SyncScheduler.syncSoon(context)
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun CadastroDialog(
    spec: CadastroSpec,
    initial: LocalRecord?,
    refs: Map<String, Map<String, String>>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(formValues(spec, initial?.record?.data)) } }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo ${spec.singular}" else "Editar ${spec.singular}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                spec.fields.forEach { f ->
                    if (f.section.isNotEmpty()) {
                        Text(f.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    }
                    val v = values[f.key] ?: ""
                    val set: (String) -> Unit = { values[f.key] = it }
                    val label = if (f.required) "${f.label} *" else f.label
                    when (f.kind) {
                        FieldKind.DECIMAL, FieldKind.DENSITY -> NumberInput(label, v, set, f.unit)
                        FieldKind.INTEGER -> NumberInput(label, v, set, f.unit, integer = true)
                        FieldKind.DATE -> DateInput(label, v, set)
                        FieldKind.REF -> SelectInput(
                            label, v,
                            (refs[f.refType] ?: emptyMap()).map { (id, name) -> Choice(id, name) }.sortedBy { it.label.lowercase() },
                            set,
                        )
                        FieldKind.CHOICE -> SelectInput(label, v, f.options.map { Choice(it, it) }, set)
                        FieldKind.TEXT -> TextInput(label, v, set)
                    }
                }
                error?.let { Text(it, color = Color(0xFFC62828)) }
                if (onDelete != null) {
                    if (confirmDelete) {
                        TextButton(onClick = onDelete) { Text("Confirmar exclusão", color = Color(0xFFC62828)) }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) { Text("Excluir", color = Color(0xFFC62828)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = validateForm(spec, values)
                if (e != null) error = e else onSave(values.toMap())
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
