package br.ensaios.cliente.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import br.ensaios.cliente.ui.theme.appColors
import br.ensaios.shared.CadastroSpec
import br.ensaios.shared.Cadastros
import br.ensaios.shared.FieldKind
import br.ensaios.shared.Perm
import br.ensaios.shared.UserInfo

/** Tela com todos os cadastros. */
@Composable
fun CadastrosMenuScreen(onOpen: (CadastroSpec) -> Unit) {
    val context = LocalContext.current
    val db = remember { LocalDb.get(context) }
    val changes by LocalDb.changes.collectAsState()
    AppScreen(title = "Cadastros", subtitle = "Escolha o que cadastrar") {
        Cadastros.all.chunked(2).forEach { par ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                par.forEach { spec ->
                    val n = remember(changes, spec) { db.listByType(spec.type).size }
                    Tile(Icons.Filled.List, spec.title, "$n cadastrado(s)", Modifier.weight(1f)) { onOpen(spec) }
                }
                if (par.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
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

    AppScreen(
        title = spec.title,
        subtitle = "${list.size} cadastrado(s)",
        onBack = onBack,
        fab = if (canEdit) ({ creating = true }) else null,
    ) {
        if (!canEdit) MutedText("Você pode consultar, mas não alterar os cadastros.")
        if (list.isEmpty()) EmptyText("Nada cadastrado. Toque em + para cadastrar.")
        list.forEach { item ->
            AppCard(onClick = if (canEdit) ({ editing = item }) else null) {
                TitleText(item.record.data.text(spec.titleKey))
                spec.fields
                    .filter { it.key != spec.titleKey && it.section.isEmpty() }
                    .take(3)
                    .forEach { f ->
                        val v = item.record.data.display(f, refs[f.refType] ?: emptyMap())
                        if (v.isNotEmpty()) InfoRow(f.label, v)
                    }
                if (item.pending) Pill("Aguardando envio", PillKind.WARN)
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
                    if (f.section.isNotEmpty()) SectionLabel(f.section)
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
                error?.let { Text(it, color = appColors.bad, fontWeight = FontWeight.SemiBold) }
                if (onDelete != null) {
                    if (confirmDelete) {
                        TextButton(onClick = onDelete) { Text("Confirmar exclusão", color = appColors.bad) }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) { Text("Excluir", color = appColors.bad) }
                    }
                }
                Column(Modifier.padding(bottom = 4.dp)) {}
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = validateForm(spec, values)
                if (e != null) error = e else onSave(values.toMap())
            }) { Text("Salvar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
