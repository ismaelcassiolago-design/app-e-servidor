package br.ensaios.cliente.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.ensaios.cliente.data.LocalDb
import br.ensaios.cliente.data.Session
import br.ensaios.cliente.net.ApiClient
import br.ensaios.cliente.net.ApiException
import br.ensaios.cliente.sync.SyncEngine
import br.ensaios.shared.LoginRequest
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onOpenConnection: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val serverUrl = Session.serverUrl(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenConnection) {
                Icon(Icons.Filled.Settings, contentDescription = "Configurar conexão")
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Controle de Ensaios", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (serverUrl.isEmpty()) "Toque na engrenagem para configurar o servidor."
            else "Servidor: $serverUrl",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuário") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color(0xFFC62828)) }
        Button(
            enabled = !loading && username.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = {
                loading = true
                error = null
                scope.launch {
                    try {
                        val resp = ApiClient(Session.serverUrl(context)).login(
                            LoginRequest(username.trim(), password, Build.MODEL ?: "")
                        )
                        val previous = Session.lastUserId(context)
                        if (previous != null && previous != resp.user.id) {
                            // Outro usuário neste celular: começa com os dados limpos.
                            LocalDb.get(context).clearAll()
                        }
                        Session.login(context, resp.token, resp.user)
                        SyncEngine.sync(context)
                    } catch (e: ApiException) {
                        error = e.message
                    } catch (e: Exception) {
                        error = e.message ?: "Erro ao entrar"
                    } finally {
                        loading = false
                    }
                }
            },
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.height(24.dp)) else Text("Entrar")
        }
    }
}
