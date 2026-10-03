package br.ensaios.servidor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import br.ensaios.servidor.ui.theme.Palette
import br.ensaios.servidor.ui.theme.colorsFor
import br.ensaios.servidor.Prefs
import br.ensaios.servidor.ServerService
import br.ensaios.servidor.db.ServerDb
import br.ensaios.servidor.db.ServerStats
import java.net.Inet4Address
import java.net.NetworkInterface

private fun localAddresses(): List<String> =
    try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress ?: "" }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }

@Composable
fun StatusScreen(openBatterySettings: () -> Unit) {
    val context = LocalContext.current
    val db = remember { ServerDb.get(context) }
    val running by ServerService.running.collectAsState()
    val lastError by ServerService.lastError.collectAsState()
    var stats by remember { mutableStateOf(ServerStats(0, 0, 0)) }
    var serverName by remember { mutableStateOf(db.getMeta("server_name") ?: "Servidor de ensaios") }
    var publicUrl by remember { mutableStateOf(Prefs.publicUrl(context)) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(running) { stats = db.stats() }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(if (running) Color(0xFF2E7D32) else Color(0xFFC62828), CircleShape)
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        if (running) "Servidor LIGADO" else "Servidor DESLIGADO",
                        fontWeight = FontWeight.Bold,
                    )
                }
                lastError?.let { Text("Erro: $it", color = Color(0xFFC62828)) }
                Text("Porta: ${ServerService.PORT}")
                val ips = localAddresses()
                if (ips.isNotEmpty()) {
                    Text("Na mesma rede Wi-Fi: " + ips.joinToString { "http://$it:${ServerService.PORT}" })
                }
                if (publicUrl.isNotBlank()) Text("Pela internet: $publicUrl", fontWeight = FontWeight.SemiBold)
                Text("Usuários: ${stats.users}   Registros: ${stats.records}   Alterações: ${stats.maxSeq}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) {
                        OutlinedButton(onClick = { ServerService.stop(context) }) { Text("Desligar") }
                    } else {
                        Button(onClick = { ServerService.start(context) }) { Text("Ligar") }
                    }
                    OutlinedButton(onClick = { stats = db.stats() }) { Text("Atualizar") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Configuração", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it; saved = false },
                    label = { Text("Nome do servidor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = publicUrl,
                    onValueChange = { publicUrl = it; saved = false },
                    label = { Text("Endereço pela internet (Cloudflare)") },
                    placeholder = { Text("https://ensaios.seudominio.com.br") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    db.setMeta("server_name", serverName.trim())
                    Prefs.setPublicUrl(context, publicUrl)
                    saved = true
                }) { Text("Salvar") }
                if (saved) Text("Salvo", color = Color(0xFF2E7D32))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cor do app", fontWeight = FontWeight.SemiBold)
                val atual by ServerTheme.palette.collectAsState()
                Palette.values().filter { it != Palette.SOL_FORTE }.forEach { p ->
                    val selected = p.name == atual
                    OutlinedButton(
                        onClick = { Prefs.setPalette(context, p.name); ServerTheme.palette.value = p.name },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(Modifier.size(18.dp).background(colorsFor(p).header, CircleShape))
                        Spacer(Modifier.padding(4.dp))
                        Text(p.label + if (selected) "  (em uso)" else "", fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Manter sempre ligado", fontWeight = FontWeight.SemiBold)
                Text(
                    "Deixe o celular na tomada e tire este app e o Termux da economia de bateria. " +
                        "Sem isso, o Android pode fechar o servidor."
                )
                OutlinedButton(onClick = openBatterySettings) { Text("Abrir economia de bateria") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
