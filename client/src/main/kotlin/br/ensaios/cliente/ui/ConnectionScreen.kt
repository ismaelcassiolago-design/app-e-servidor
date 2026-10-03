package br.ensaios.cliente.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.ensaios.cliente.data.Session
import br.ensaios.cliente.net.ApiClient
import kotlinx.coroutines.launch

@Composable
fun ConnectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(Session.serverUrl(context)) }
    var result by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
            Text("Conexão com o servidor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Text("Endereço do servidor de ensaios. Pela internet: o endereço da Cloudflare. Na mesma rede: o IP mostrado no app servidor.")
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; result = null },
            label = { Text("Endereço do servidor") },
            placeholder = { Text("https://ensaios.seudominio.com.br") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !testing && url.isNotBlank(),
                onClick = {
                    testing = true
                    result = null
                    scope.launch {
                        try {
                            val ping = ApiClient(Session.normalizeUrl(url)).ping()
                            ok = true
                            result = "Conectado: ${ping.serverName} (versão ${ping.serverVersion})"
                        } catch (e: Exception) {
                            ok = false
                            result = e.message ?: "Falha na conexão"
                        } finally {
                            testing = false
                        }
                    }
                },
            ) { Text(if (testing) "Testando..." else "Testar conexão") }
            Button(
                enabled = url.isNotBlank(),
                onClick = {
                    Session.setServerUrl(context, url)
                    onBack()
                },
            ) { Text("Salvar") }
        }
        result?.let { Text(it, color = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828)) }
    }
}
