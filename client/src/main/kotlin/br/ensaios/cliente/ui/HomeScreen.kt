package br.ensaios.cliente.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ensaios.cliente.data.AppSettings
import br.ensaios.cliente.data.Ensaios
import br.ensaios.cliente.data.LocalDb
import br.ensaios.cliente.data.Producao
import br.ensaios.cliente.sync.SyncEngine
import br.ensaios.cliente.ui.theme.appColors
import br.ensaios.shared.UserInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    user: UserInfo,
    onProducao: () -> Unit,
    onRelatorios: () -> Unit,
    onResumos: () -> Unit,
    onCadastros: () -> Unit,
    onConta: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { LocalDb.get(context) }
    val status by SyncEngine.status.collectAsState()
    val changes by LocalDb.changes.collectAsState()
    val casasGc by AppSettings.casasGc.collectAsState()
    val pending = remember(changes, status) { db.pendingCount() }
    val hora = remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")) }
    val c = appColors

    val hoje = LocalDate.now().toString()
    val segsHoje = remember(changes) { Producao.todosSegmentos(db).filter { it.data == hoje } }
    val ensaiosHoje = remember(changes, casasGc) { segsHoje.flatMap { s -> s.id?.let { Ensaios.doSegmento(db, it, casasGc) } ?: emptyList() } }
    val producao = segsHoje.sumOf { it.extensao ?: 0L }

    // Sincroniza ao abrir o app.
    LaunchedEffect(Unit) { SyncEngine.sync(context) }

    AppScreen(
        title = "Controle de Ensaios",
        subtitle = "Olá, ${user.name} · nº %02d".format(user.number),
        actions = {
            IconButton(onClick = onConta) { Icon(Icons.Filled.Settings, contentDescription = "Configurações", tint = c.headerInk) }
        },
    ) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        status.running -> Pill("Sincronizando…", PillKind.PRIMARY)
                        !status.ok -> Pill("Falha na sincronização", PillKind.BAD)
                        pending > 0 -> Pill("$pending aguardando envio", PillKind.WARN)
                        else -> Pill("● Sincronizado", PillKind.OK)
                    }
                    MutedText(
                        if (status.lastSuccessAt > 0) "Última: ${hora.format(Date(status.lastSuccessAt))}" else "Ainda não sincronizado"
                    )
                    if (!status.ok && status.message.isNotEmpty()) Text(status.message, color = c.bad, fontSize = 12.sp)
                }
                Button(
                    enabled = !status.running,
                    onClick = { scope.launch { SyncEngine.sync(context) } },
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary),
                ) { Text("Sincronizar", fontWeight = FontWeight.Bold) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("Produção hoje", "$producao m", "${segsHoje.size} segmento(s)", Modifier.weight(1f))
            Kpi(
                "Ensaios hoje", "${ensaiosHoje.size}",
                "${ensaiosHoje.count { it.status == "ok" }} ✓ · ${ensaiosHoje.count { it.status == "bad" }} ✗",
                Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Icons.Filled.DateRange, "Dias de produção", "segmentos e ensaios", Modifier.weight(1f), onProducao)
            Tile(Icons.Filled.Share, "Relatórios gerados", "PDFs salvos", Modifier.weight(1f), onRelatorios)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Icons.Filled.Send, "Últimos resumos", "imagem e texto", Modifier.weight(1f), onResumos)
            Tile(Icons.Filled.List, "Cadastros", "clientes, frascos…", Modifier.weight(1f), onCadastros)
        }
    }
}

/** Telas que ainda vão chegar nas próximas versões. */
@Composable
fun EmBreveScreen(title: String, texto: String, onBack: (() -> Unit)?) {
    AppScreen(title = title, onBack = onBack) {
        AppCard {
            TitleText("Em breve")
            MutedText(texto)
        }
    }
}
