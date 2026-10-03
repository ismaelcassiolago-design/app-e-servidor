package br.ensaios.servidor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import br.ensaios.servidor.ui.ServerHeader
import br.ensaios.servidor.ui.ServerTheme
import br.ensaios.servidor.ui.theme.AppTheme
import br.ensaios.servidor.ui.theme.Palette
import br.ensaios.servidor.ui.theme.appColors
import br.ensaios.servidor.ui.HistoryScreen
import br.ensaios.servidor.ui.StatusScreen
import br.ensaios.servidor.ui.UsersScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (Prefs.autoStart(this)) ServerService.start(this)

        ServerTheme.palette.value = Prefs.palette(this)
        setContent {
            val paletteName by ServerTheme.palette.collectAsState()
            val palette = try { Palette.valueOf(paletteName) } catch (e: Exception) { Palette.VERDE }
            AppTheme(palette) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ServerApp(
                        openBatterySettings = {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerApp(openBatterySettings: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Servidor") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Usuários") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("Histórico") },
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).background(appColors.background)) {
            Column {
                ServerHeader(
                    when (tab) { 0 -> "Servidor de ensaios"; 1 -> "Usuários"; else -> "Histórico" },
                    when (tab) { 0 -> "versão ${BuildConfig.VERSION_NAME}"; 1 -> "Quem pode entrar e o que pode fazer"; else -> "Quem criou e alterou cada registro" },
                )
                when (tab) {
                    0 -> StatusScreen(openBatterySettings)
                    1 -> UsersScreen()
                    else -> HistoryScreen()
                }
            }
        }
    }
}
