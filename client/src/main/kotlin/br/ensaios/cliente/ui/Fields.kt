package br.ensaios.cliente.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ensaios.cliente.ui.theme.appColors
import br.ensaios.shared.Num
import java.time.LocalDate

/** Opção de uma lista de escolha. */
data class Choice(val value: String, val label: String)

private val fieldShape = RoundedCornerShape(10.dp)

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = appColors.primary,
    focusedLabelColor = appColors.primary,
    cursorColor = appColors.primary,
    unfocusedContainerColor = appColors.surface,
    focusedContainerColor = appColors.surface,
)

/** Dois campos lado a lado. */
@Composable
fun TwoFields(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Campo de texto simples. */
@Composable
fun TextInput(
    label: String, value: String, onChange: (String) -> Unit,
    singleLine: Boolean = true, modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        shape = fieldShape,
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Campo numérico: abre o teclado numérico e aceita vírgula ou ponto. */
@Composable
fun NumberInput(
    label: String, value: String, onChange: (String) -> Unit,
    unit: String = "", integer: Boolean = false, modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            onChange(new.filter { it.isDigit() || (!integer && (it == ',' || it == '.')) || it == '-' })
        },
        label = { Text(if (unit.isEmpty()) label else "$label ($unit)", maxLines = 1) },
        singleLine = true,
        shape = fieldShape,
        colors = fieldColors(),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier.fillMaxWidth(),
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
fun KmInput(label: String, digits: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = digits,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(7)) },
        label = { Text("$label (km+m)", maxLines = 1) },
        placeholder = { Text("168+340") },
        singleLine = true,
        shape = fieldShape,
        colors = fieldColors(),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        visualTransformation = KmTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Caixa com rótulo pequeno e valor, que abre uma escolha ao tocar. */
@Composable
private fun PickerBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val c = appColors
    Column(
        modifier
            .fillMaxWidth()
            .clip(fieldShape)
            .background(c.surface)
            .border(BorderStroke(1.dp, c.line.copy(alpha = 1f)), fieldShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp, color = c.muted, maxLines = 1)
        Text(
            value.ifEmpty { "Tocar para escolher" },
            fontSize = 17.sp,
            fontWeight = if (value.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
            color = if (value.isEmpty()) c.muted else c.ink,
            maxLines = 1,
        )
    }
}

/** Campo de data com calendário. Valor no formato 2026-10-03. */
@Composable
fun DateInput(label: String, iso: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    PickerBox(label, Num.date(iso), modifier) {
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
    }
}

/** Escolha numa lista, com busca. */
@Composable
fun SelectInput(
    label: String, value: String, choices: List<Choice>, onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val current = choices.firstOrNull { it.value == value }?.label ?: ""
    PickerBox(label, current, modifier) { open = true }
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
                            label = { Text("Buscar") }, singleLine = true, shape = fieldShape,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (filtered.isEmpty()) Text("Nada cadastrado. Cadastre em Cadastros.")
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.value }) { ch ->
                            Text(
                                ch.label,
                                fontSize = 17.sp,
                                fontWeight = if (ch.value == value) FontWeight.Bold else FontWeight.Normal,
                                color = if (ch.value == value) appColors.primary else appColors.ink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onChange(ch.value)
                                        open = false
                                    }
                                    .padding(vertical = 14.dp),
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

/** Valor calculado mostrado como campo só de leitura (ex.: linha 3 = 1 − 2). */
@Composable
fun CalcField(label: String, value: String, modifier: Modifier = Modifier) {
    val c = appColors
    Column(
        modifier
            .fillMaxWidth()
            .clip(fieldShape)
            .background(c.primarySoft)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp, color = c.muted, maxLines = 1)
        Text(value.ifEmpty { "—" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.primary, maxLines = 1)
    }
}
