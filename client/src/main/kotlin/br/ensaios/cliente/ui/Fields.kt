package br.ensaios.cliente.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.ensaios.shared.Num
import java.time.LocalDate

/** Opção de uma lista de escolha. */
data class Choice(val value: String, val label: String)

/** Campo de texto simples. */
@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Campo numérico: abre o teclado numérico e aceita vírgula ou ponto. */
@Composable
fun NumberInput(label: String, value: String, onChange: (String) -> Unit, unit: String = "", integer: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val cleaned = new.filter { it.isDigit() || (!integer && (it == ',' || it == '.')) || it == '-' }
            onChange(cleaned)
        },
        label = { Text(if (unit.isEmpty()) label else "$label ($unit)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Mostra "168340" como "168+340" (o + entra sozinho antes dos 3 últimos dígitos). */
private object KmTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length <= 3) return TransformedText(text, OffsetMapping.Identity)
        val p = raw.length - 3
        val out = raw.substring(0, p) + "+" + raw.substring(p)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset > p) offset + 1 else offset
            override fun transformedToOriginal(offset: Int): Int = if (offset > p) offset - 1 else offset
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/** Campo de posição em km + metros (ex.: 168+340). Guarda só os dígitos. */
@Composable
fun KmInput(label: String, digits: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = digits,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(7)) },
        label = { Text("$label (km+m)") },
        placeholder = { Text("168+340") },
        singleLine = true,
        visualTransformation = KmTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Campo de data com calendário. Valor no formato 2026-10-03. */
@Composable
fun DateInput(label: String, iso: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val d = try {
                LocalDate.parse(iso)
            } catch (e: Exception) {
                LocalDate.now()
            }
            DatePickerDialog(
                context,
                { _, y, m, day -> onChange(LocalDate.of(y, m + 1, day).toString()) },
                d.year, d.monthValue - 1, d.dayOfMonth,
            ).show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (iso.isEmpty()) "$label: escolher" else "$label: ${Num.date(iso)}")
    }
}

/** Escolha numa lista, com busca. */
@Composable
fun SelectInput(label: String, value: String, choices: List<Choice>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = choices.firstOrNull { it.value == value }?.label
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (current == null) "$label: escolher" else "$label: $current")
    }
    if (open) {
        var search by remember { mutableStateOf("") }
        val filtered = choices.filter { it.label.contains(search.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (choices.size > 6) {
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (filtered.isEmpty()) Text("Nada cadastrado. Cadastre em Cadastros.")
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.value }) { c ->
                            Text(
                                c.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onChange(c.value)
                                        open = false
                                    }
                                    .padding(vertical = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                if (value.isNotEmpty()) {
                    TextButton(onClick = { onChange(""); open = false }) { Text("Limpar") }
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Fechar") } },
        )
    }
}
