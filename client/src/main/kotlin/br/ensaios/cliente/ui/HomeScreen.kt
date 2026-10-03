package br.ensaios.cliente.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.ensaios.cliente.BuildConfig
import br.ensaios.cliente.Screen
import br.ensaios.cliente.data.LocalDb
import br.ensaios.cliente.data.Session
import br.ensaios.cliente.net.ApiClient
import br.ensaios.cliente.sync.SyncEngine
import br.ensaios.shared.ChangePasswordRequest
import br.ensaios.shared.UserInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(user: UserInfo, onOpen: (Screen) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { LocalDb.get(context) }
    val status by SyncEngine.status.collectAsState()
    val changes by LocalDb.changes.collectAsState()
    val pending = remember(changes, status) { db.pendingCount() }
    var askLogout by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")) }

    // Sincroniza ao abrir o app.
    LaunchedEffect(Unit) { SyncEngine.sync(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Olá, ${user.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Usuário nº %02d · versão %s".format(user.number, BuildConfig.VERSION_NAME),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sincronização", fontWeight = FontWeight.SemiBold)
                Text(
                    if (status.lastSuccessAt > 0) "Última: ${fmt.format(Date(status.lastSuccessAt))}"
                    else "Ainda não sincronizado"
                )
                Text(
                    if (pending == 0) "Nada aguardando envio" else "$pending alteração(ões) aguardando envio",
                    color = if (pending == 0) Color(0xFF2E7D32) else Color(0xFFB26A00),
                    fontWeight = FontWeight.SemiBold,
                )
                if (status.message.isNotEmpty()) {
                    Text(status.message, color = if (status.ok) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFC62828))
                }
                Button(
                    enabled = !status.running,
                    onClick = { scope.launch { SyncEngine.sync(context) } },
                ) { Text(if (status.running) "Sincronizando..." else "Sincronizar agora") }
            }
        }

        Text("Cadastros", fontWeight = FontWeight.SemiBold)
        MenuButton("Clientes") { onOpen(Screen.CLIENTES) }

        Spacer(Modifier.height(8.dp))
        Text("Conta", fontWeight = FontWeight.SemiBold)
        MenuButton("Trocar senha") { changingPassword = true }
        MenuButton("Sair") { askLogout = true }
    }

    if (askLogout) {
        AlertDialog(
            onDismissRequest = { askLogout = false },
            title = { Text("Sair") },
            text = {
                Text(
                    if (pending > 0) "Há $pending alteração(ões) ainda não enviadas. Elas ficam guardadas neste celular e serão enviadas quando você entrar de novo com o mesmo usuário."
                    else "Deseja sair do app?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askLogout = false
                    Session.logout(context)
                }) { Text("Sair") }
            },
            dismissButton = { TextButton(onClick = { askLogout = false }) { Text("Cancelar") } },
        )
    }

    if (changingPassword) {
        ChangePasswordDialog(onDismiss = { changingPassword = false })
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trocar senha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = old, onValueChange = { old = it }, label = { Text("Senha atual") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = new1, onValueChange = { new1 = it }, label = { Text("Nova senha") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = new2, onValueChange = { new2 = it }, label = { Text("Repita a nova senha") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                )
                message?.let { Text(it, color = if (done) Color(0xFF2E7D32) else Color(0xFFC62828)) }
                Row {}
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (done) {
                    onDismiss(); return@TextButton
                }
                if (new1 != new2) {
                    message = "As senhas novas não são iguais"
                    return@TextButton
                }
                scope.launch {
                    try {
                        ApiClient(Session.serverUrl(context), Session.token(context))
                            .changePassword(ChangePasswordRequest(old, new1))
                        done = true
                        message = "Senha alterada"
                    } catch (e: Exception) {
                        message = e.message ?: "Erro"
                    }
                }
            }) { Text(if (done) "Fechar" else "Salvar") }
        },
        dismissButton = { if (!done) TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
