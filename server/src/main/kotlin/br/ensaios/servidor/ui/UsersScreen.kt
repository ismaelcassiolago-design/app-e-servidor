package br.ensaios.servidor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.ensaios.servidor.db.ServerDb
import br.ensaios.shared.Perm
import br.ensaios.shared.UserInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsersScreen() {
    val context = LocalContext.current
    val db = remember { ServerDb.get(context) }
    var users by remember { mutableStateOf(db.listUsers()) }
    var lastSeen by remember { mutableStateOf(db.lastSeenByUser()) }
    var editing by remember { mutableStateOf<UserInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")) }

    fun reload() {
        users = db.listUsers()
        lastSeen = db.lastSeenByUser()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Usuários",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { creating = true }) { Text("Novo usuário") }
        }
        if (users.isEmpty()) {
            Text("Nenhum usuário ainda. Crie o primeiro para os celulares de campo poderem entrar.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users, key = { it.id }) { u ->
                Card(modifier = Modifier.fillMaxWidth().clickable { editing = u }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "%02d · %s".format(u.number, u.name),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Usuário: ${u.username}" + if (u.isAdmin) " · administrador" else "")
                        if (!u.active) Text("BLOQUEADO", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                        val seen = lastSeen[u.id]
                        Text(
                            if (seen != null) "Último acesso: ${fmt.format(Date(seen))}" else "Ainda não entrou",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        UserDialog(
            user = null,
            onDismiss = { creating = false },
            onSave = { name, username, password, isAdmin, active, perms ->
                db.createUser(username, name, password, isAdmin, perms)
                creating = false
                reload()
            },
        )
    }
    editing?.let { u ->
        UserDialog(
            user = u,
            onDismiss = { editing = null },
            onSave = { name, _, password, isAdmin, active, perms ->
                db.updateUser(u.id, name, isAdmin, active, perms)
                if (password.isNotEmpty()) db.setPassword(u.id, password)
                editing = null
                reload()
            },
        )
    }
}

@Composable
private fun UserDialog(
    user: UserInfo?,
    onDismiss: () -> Unit,
    onSave: (name: String, username: String, password: String, isAdmin: Boolean, active: Boolean, perms: Set<String>) -> Unit,
) {
    var name by remember { mutableStateOf(user?.name ?: "") }
    var username by remember { mutableStateOf(user?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(user?.isAdmin ?: false) }
    var active by remember { mutableStateOf(user?.active ?: true) }
    var perms by remember { mutableStateOf(user?.permissions ?: Perm.profiles.getValue("Laboratorista")) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (user == null) "Novo usuário" else "Editar ${user.name}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (user == null) {
                    OutlinedTextField(
                        value = username, onValueChange = { username = it },
                        label = { Text("Usuário (para entrar)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(if (user == null) "Senha" else "Nova senha (deixe vazio para manter)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (user != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ativo", modifier = Modifier.weight(1f))
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Administrador (acesso total)", modifier = Modifier.weight(1f))
                    Switch(checked = isAdmin, onCheckedChange = { isAdmin = it })
                }
                HorizontalDivider()
                Text("Perfis prontos", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Perm.profiles.forEach { (profile, set) ->
                        OutlinedButton(onClick = { perms = set }) { Text(profile, maxLines = 1) }
                    }
                }
                Text("Permissões", fontWeight = FontWeight.SemiBold)
                Perm.all.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = p in perms,
                            onCheckedChange = { checked -> perms = if (checked) perms + p else perms - p },
                        )
                        Text(Perm.labels[p] ?: p)
                    }
                }
                error?.let { Text(it, color = Color(0xFFC62828)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    error = null
                    onSave(name, username, password, isAdmin, active, perms)
                } catch (e: Exception) {
                    error = e.message ?: "Erro ao salvar"
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
