package br.ensaios.servidor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.ensaios.servidor.db.ServerDb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Nomes legíveis dos tipos de registro. */
private val typeNames = mapOf(
    "cad_cliente" to "Cliente",
)

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val db = remember { ServerDb.get(context) }
    var entries by remember { mutableStateOf(db.history()) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Histórico de alterações",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { entries = db.history() }) { Text("Atualizar") }
        }
        if (entries.isEmpty()) Text("Nenhuma alteração recebida ainda.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { e ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${e.userName ?: "?"} ${e.action} ${typeNames[e.type] ?: e.type}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (e.summary.isNotEmpty()) Text(e.summary)
                        Text(fmt.format(Date(e.at)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
