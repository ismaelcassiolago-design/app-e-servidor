package br.ensaios.cliente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import br.ensaios.cliente.data.Session
import br.ensaios.cliente.sync.SyncEngine
import br.ensaios.cliente.sync.SyncScheduler
import br.ensaios.cliente.ui.ClientesScreen
import br.ensaios.cliente.ui.ConnectionScreen
import br.ensaios.cliente.ui.HomeScreen
import br.ensaios.cliente.ui.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.load(this)
        SyncEngine.loadStatus(this)
        SyncScheduler.schedulePeriodic(this)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2E7D32),
                    secondary = Color(0xFF1F5FAD),
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ClientApp()
                }
            }
        }
    }
}

/** Telas do app. Novas telas entram aqui. */
enum class Screen { HOME, CLIENTES }

@Composable
private fun ClientApp() {
    val user by Session.user.collectAsState()
    var showConnection by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.HOME) }

    val current = user
    if (current == null) {
        if (showConnection) {
            BackHandler { showConnection = false }
            ConnectionScreen(onBack = { showConnection = false })
        } else {
            LoginScreen(onOpenConnection = { showConnection = true })
        }
        return
    }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
    when (screen) {
        Screen.HOME -> HomeScreen(user = current, onOpen = { screen = it })
        Screen.CLIENTES -> ClientesScreen(user = current, onBack = { screen = Screen.HOME })
    }
}
