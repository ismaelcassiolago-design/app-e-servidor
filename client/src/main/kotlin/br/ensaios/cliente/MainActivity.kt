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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import br.ensaios.cliente.data.Session
import br.ensaios.cliente.sync.SyncEngine
import br.ensaios.cliente.sync.SyncScheduler
import br.ensaios.cliente.ui.CadastroListScreen
import br.ensaios.cliente.ui.CadastrosMenuScreen
import br.ensaios.cliente.ui.ConnectionScreen
import br.ensaios.cliente.ui.HomeScreen
import br.ensaios.cliente.ui.LoginScreen
import br.ensaios.cliente.ui.SegmentoDetailScreen
import br.ensaios.cliente.ui.SegmentoEditScreen
import br.ensaios.cliente.ui.SegmentosScreen
import br.ensaios.shared.CadastroSpec

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
                    primaryContainer = Color(0xFFDCEFD9),
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
sealed class Route {
    data object Home : Route()
    data object CadastrosMenu : Route()
    data class Cadastro(val spec: CadastroSpec) : Route()
    data object Segmentos : Route()
    data class SegmentoDetail(val id: String) : Route()
    data class SegmentoEdit(val id: String?) : Route()
}

@Composable
private fun ClientApp() {
    val user by Session.user.collectAsState()
    var showConnection by remember { mutableStateOf(false) }
    // Pilha de telas: o botão voltar volta para a anterior.
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val go: (Route) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.size - 1) }

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

    BackHandler(enabled = stack.size > 1) { back() }
    when (val route = stack.last()) {
        Route.Home -> HomeScreen(user = current, onOpen = go)
        Route.CadastrosMenu -> CadastrosMenuScreen(onBack = back, onOpen = { go(Route.Cadastro(it)) })
        is Route.Cadastro -> CadastroListScreen(spec = route.spec, user = current, onBack = back)
        Route.Segmentos -> SegmentosScreen(
            user = current,
            onBack = back,
            onOpen = { go(Route.SegmentoDetail(it)) },
            onNew = { go(Route.SegmentoEdit(null)) },
        )
        is Route.SegmentoDetail -> SegmentoDetailScreen(
            user = current,
            id = route.id,
            onBack = back,
            onEdit = { go(Route.SegmentoEdit(route.id)) },
        )
        is Route.SegmentoEdit -> SegmentoEditScreen(
            user = current,
            id = route.id,
            onDone = { savedId ->
                back()
                // Segmento novo salvo: abre os detalhes dele.
                if (route.id == null && savedId != null) go(Route.SegmentoDetail(savedId))
            },
        )
    }
}
