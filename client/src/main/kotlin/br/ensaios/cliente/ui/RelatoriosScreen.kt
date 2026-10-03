package br.ensaios.cliente.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import br.ensaios.cliente.data.Dia
import br.ensaios.cliente.report.ModeloPdf
import br.ensaios.cliente.report.gerarRelatorioDia
import br.ensaios.cliente.report.pastaRelatorios
import br.ensaios.cliente.ui.theme.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun uriDe(context: Context, file: File) =
    FileProvider.getUriForFile(context, "br.ensaios.cliente.arquivos", file)

/** Abre o PDF no leitor do celular. */
fun abrirPdf(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriDe(context, file), "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum leitor de PDF instalado. Use Compartilhar.", Toast.LENGTH_LONG).show()
    }
}

/** Compartilha o PDF (WhatsApp, e-mail, Drive...). */
fun compartilharPdf(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uriDe(context, file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Enviar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Lista dos PDFs já gerados neste celular. */
@Composable
fun RelatoriosScreen() {
    val context = LocalContext.current
    var versao by remember { mutableIntStateOf(0) }
    val arquivos = remember(versao) {
        (pastaRelatorios(context).listFiles()?.toList() ?: emptyList())
            .filter { it.name.endsWith(".pdf") }
            .sortedByDescending { it.lastModified() }
    }
    var apagar by remember { mutableStateOf<File?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")) }

    AppScreen(title = "Relatórios gerados", subtitle = "${arquivos.size} PDF(s) neste celular") {
        if (arquivos.isEmpty()) {
            EmptyText("Nenhum relatório ainda. Para gerar, abra um dia de produção e toque em PDF.")
        }
        arquivos.forEach { f ->
            AppCard(onClick = { abrirPdf(context, f) }) {
                TitleText(f.nameWithoutExtension.replace('_', ' '))
                MutedText("Gerado em ${fmt.format(Date(f.lastModified()))} · ${f.length() / 1024} KB")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { abrirPdf(context, f) }) { Text("Abrir") }
                    TextButton(onClick = { compartilharPdf(context, f) }) { Text("Compartilhar") }
                    TextButton(onClick = { apagar = f }) { Text("Excluir", color = appColors.bad) }
                }
            }
        }
    }

    apagar?.let { f ->
        ConfirmDialog("Excluir relatório", "O arquivo ${f.name} será apagado deste celular.", onDismiss = { apagar = null }) {
            f.delete()
            apagar = null
            versao++
        }
    }
}

/** Janela para escolher o modelo e gerar o PDF de um dia. */
@Composable
fun GerarPdfDialog(dia: Dia, autor: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gerando by remember { mutableStateOf(false) }
    var pronto by remember { mutableStateOf<File?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!gerando) onDismiss() },
        title = { Text(if (pronto == null) "Gerar PDF do dia" else "PDF pronto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val arquivo = pronto
                when {
                    gerando -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Gerando…")
                    }
                    arquivo != null -> {
                        Text(arquivo.name)
                        PrimaryButton("Abrir") { abrirPdf(context, arquivo) }
                        SecondaryButton("Compartilhar") { compartilharPdf(context, arquivo) }
                    }
                    else -> {
                        MutedText("Só entram ensaios concluídos (rascunhos ficam de fora).")
                        ModeloPdf.values().forEach { m ->
                            SecondaryButton(m.label) {
                                gerando = true
                                scope.launch {
                                    try {
                                        pronto = withContext(Dispatchers.IO) { gerarRelatorioDia(context, dia, m, autor) }
                                    } catch (e: Exception) {
                                        erro = e.message ?: e.toString()
                                    } finally {
                                        gerando = false
                                    }
                                }
                            }
                        }
                    }
                }
                erro?.let { Text("Erro: $it", color = appColors.bad) }
            }
        },
        confirmButton = { if (!gerando) TextButton(onClick = onDismiss) { Text("Fechar") } },
        modifier = Modifier,
    )
}
