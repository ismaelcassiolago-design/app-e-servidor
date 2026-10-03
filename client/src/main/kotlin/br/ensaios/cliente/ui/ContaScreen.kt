package br.ensaios.cliente.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.ensaios.cliente.BuildConfig
import br.ensaios.cliente.data.AppSettings
import br.ensaios.cliente.data.LocalDb
import br.ensaios.cliente.data.Session
import br.ensaios.cliente.net.ApiClient
import br.ensaios.cliente.ui.theme.Palette
import br.ensaios.cliente.ui.theme.appColors
import br.ensaios.cliente.ui.theme.colorsFor
import br.ensaios.shared.ChangePasswordRequest
import br.ensaios.shared.UserInfo
import kotlinx.coroutines.launch

/** Configurações do app e da conta. */
@Composable
fun ContaScreen(user: UserInfo, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette by AppSettings.palette.collectAsState()
    val casasTaxa by AppSettings.casasTaxa.collectAsState()
    val casasGc by AppSettings.casasGc.collectAsState()
    var askLogout by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    val pending = remember { LocalDb.get(context).pendingCount() }
    val c = appColors

    AppScreen(title = "Configurações", subtitle = "${user.name} · versão ${BuildConfig.VERSION_NAME}", onBack = onBack) {
        SectionLabel("Cor do app")
        AppCard {
            Palette.values().filter { it != Palette.SOL_FORTE }.forEach { p ->
                val pc = colorsFor(p)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { AppSettings.setPalette(context, p) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(pc.header))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(28.dp).clip(CircleShape).background(pc.primary))
                    Spacer(Modifier.width(12.dp))
                    Text(p.label, modifier = Modifier.weight(1f), fontWeight = if (p == palette) FontWeight.Bold else FontWeight.Normal)
                    if (p == palette) Pill("em uso", PillKind.PRIMARY)
                }
            }
            MutedText("O modo sol forte (☀) fica no cabeçalho de todas as telas.")
        }

        SectionLabel("Casas decimais")
        AppCard {
            Text("Taxa de aplicação", fontWeight = FontWeight.SemiBold)
            OptionRow(listOf(3 to "3 casas (5,419)", 4 to "4 casas (5,4190)"), casasTaxa) { AppSettings.setCasasTaxa(context, it) }
            Spacer(Modifier.width(4.dp))
            Text("Grau de compactação", fontWeight = FontWeight.SemiBold)
            OptionRow(listOf(1 to "1 casa", 2 to "2 casas", 3 to "3 casas"), casasGc) { AppSettings.setCasasGc(context, it) }
        }

        SectionLabel("Conexão")
        AppCard {
            InfoRow("Servidor", Session.serverUrl(context))
            InfoRow("Usuário", "${user.username} (nº %02d)".format(user.number))
        }

        SectionLabel("Conta")
        SecondaryButton("Trocar senha") { changingPassword = true }
        SecondaryButton("Sair", color = c.bad) { askLogout = true }
    }

    if (askLogout) {
        ConfirmDialog(
            "Sair",
            if (pending > 0) "Há $pending alteração(ões) ainda não enviadas. Elas ficam neste celular e serão enviadas quando você entrar de novo com o mesmo usuário."
            else "Deseja sair do app?",
            confirm = "Sair",
            onDismiss = { askLogout = false },
        ) {
            askLogout = false
            Session.logout(context)
        }
    }
    if (changingPassword) ChangePasswordDialog(onDismiss = { changingPassword = false })
}

@Composable
private fun OptionRow(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    val c = appColors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Text(
                label,
                color = if (on) c.primary else c.ink,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) c.primarySoft else c.surface)
                    .border(1.dp, if (on) c.primary else c.line, RoundedCornerShape(50))
                    .clickable { onSelect(v) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
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
                listOf(
                    Triple("Senha atual", old) { v: String -> old = v },
                    Triple("Nova senha", new1) { v: String -> new1 = v },
                    Triple("Repita a nova senha", new2) { v: String -> new2 = v },
                ).forEach { (label, value, set) ->
                    OutlinedTextField(
                        value = value, onValueChange = set, label = { Text(label) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
                message?.let { Text(it, color = if (done) appColors.ok else appColors.bad) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (done) {
                    onDismiss()
                } else if (new1 != new2) {
                    message = "As senhas novas não são iguais"
                } else {
                    scope.launch {
                        try {
                            ApiClient(Session.serverUrl(context), Session.token(context)).changePassword(ChangePasswordRequest(old, new1))
                            done = true
                            message = "Senha alterada"
                        } catch (e: Exception) {
                            message = e.message ?: "Erro"
                        }
                    }
                }
            }) { Text(if (done) "Fechar" else "Salvar") }
        },
        dismissButton = { if (!done) TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
