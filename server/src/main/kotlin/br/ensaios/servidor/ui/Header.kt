package br.ensaios.servidor.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ensaios.servidor.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow

/** Paleta escolhida (muda na hora quando o usuário troca). */
object ServerTheme {
    val palette = MutableStateFlow("VERDE")
}

/** Barra colorida do topo. */
@Composable
fun ServerHeader(title: String, subtitle: String? = null) {
    val c = appColors
    val context = LocalContext.current
    SideEffect { (context as? Activity)?.window?.statusBarColor = c.header.toArgb() }
    Column(Modifier.fillMaxWidth().background(c.header).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, color = c.headerInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = c.headerInk.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}
