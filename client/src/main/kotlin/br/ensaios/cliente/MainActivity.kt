package br.ensaios.cliente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.ensaios.cliente.data.AppSettings
import br.ensaios.cliente.data.Session
import br.ensaios.cliente.sync.SyncEngine
import br.ensaios.cliente.sync.SyncScheduler
import br.ensaios.cliente.ui.BottomNav
import br.ensaios.cliente.ui.CadastroListScreen
import br.ensaios.cliente.ui.CadastrosMenuScreen
import br.ensaios.cliente.ui.ConnectionScreen
import br.ensaios.cliente.ui.ContaScreen
import br.ensaios.cliente.ui.DiaScreen
import br.ensaios.cliente.ui.EmBreveScreen
import br.ensaios.cliente.ui.HomeScreen
import br.ensaios.cliente.ui.InSituScreen
import br.ensaios.cliente.ui.LoginScreen
import br.ensaios.cliente.ui.MesScreen
import br.ensaios.cliente.ui.MesesScreen
import br.ensaios.cliente.ui.ProctorScreen
import br.ensaios.cliente.ui.SegmentoDetailScreen
import br.ensaios.cliente.ui.SegmentoEditScreen
import br.ensaios.cliente.ui.Tab
import br.ensaios.cliente.ui.theme.AppTheme
import br.ensaios.cliente.ui.theme.Palette
import br.ensaios.shared.CadastroSpec
import br.ensaios.shared.TipoEnsaio

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.load(this)
        SyncEngine.loadStatus(this)
        AppSettings.load(this)
        SyncScheduler.schedulePeriodic(this)

        setContent {
            val palette by AppSettings.palette.collectAsState()
            val sol by AppSettings.solForte.collectAsState()
            AppTheme(if (sol) Palette.SOL_FORTE else palette) {
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
    data object Conta : Route()
    data object Meses : Route()
    data class Mes(val id: String) : Route()
    data class Dia(val id: String) : Route()
    data class SegmentoDetail(val id: String) : Route()
    data class SegmentoEdit(val id: String?, val diaId: String?) : Route()
    data class Ensaio(val tipo: TipoEnsaio, val segmentoId: String, val id: String?) : Route()
    data object Relatorios : Route()
    data object Resumos : Route()
    data object CadastrosMenu : Route()
    data class Cadastro(val spec: CadastroSpec) : Route()
}

private fun Route.tab(): Tab = when (this) {
    Route.Home, Route.Conta, Route.Resumos -> Tab.INICIO
    Route.Relatorios -> Tab.RELATORIOS
    Route.CadastrosMenu, is Route.Cadastro -> Tab.CADASTROS
    else -> Tab.PRODUCAO
}

private fun Tab.root(): Route = when (this) {
    Tab.INICIO -> Route.Home
    Tab.PRODUCAO -> Route.Meses
    Tab.RELATORIOS -> Route.Relatorios
    Tab.CADASTROS -> Route.CadastrosMenu
}

@Composable
private fun ClientApp() {
    val user by Session.user.collectAsState()
    var showConnection by remember { mutableStateOf(false) }
    // Pilha de telas: o botão voltar volta para a anterior.
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val go: (Route) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    val goTab: (Tab) -> Unit = { t ->
        stack.clear()
        stack.add(Route.Home)
        if (t != Tab.INICIO) stack.add(t.root())
    }

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
    val route = stack.last()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (route) {
                Route.Home -> HomeScreen(
                    user = current,
                    onProducao = { goTab(Tab.PRODUCAO) },
                    onRelatorios = { goTab(Tab.RELATORIOS) },
                    onResumos = { go(Route.Resumos) },
                    onCadastros = { goTab(Tab.CADASTROS) },
                    onConta = { go(Route.Conta) },
                )
                Route.Conta -> ContaScreen(user = current, onBack = back)
                Route.Meses -> MesesScreen(user = current, onOpen = { go(Route.Mes(it)) })
                is Route.Mes -> MesScreen(user = current, mesId = route.id, onBack = back, onOpenDia = { go(Route.Dia(it)) })
                is Route.Dia -> DiaScreen(
                    user = current,
                    diaId = route.id,
                    onBack = back,
                    onOpenSegmento = { go(Route.SegmentoDetail(it)) },
                    onNewSegmento = { go(Route.SegmentoEdit(null, route.id)) },
                )
                is Route.SegmentoDetail -> SegmentoDetailScreen(
                    user = current,
                    id = route.id,
                    onBack = back,
                    onEdit = { go(Route.SegmentoEdit(route.id, null)) },
                    onOpenEnsaio = { tipo, id -> go(Route.Ensaio(tipo, route.id, id)) },
                    onNewEnsaio = { tipo -> go(Route.Ensaio(tipo, route.id, null)) },
                )
                is Route.SegmentoEdit -> SegmentoEditScreen(
                    user = current,
                    id = route.id,
                    diaId = route.diaId,
                    onDone = { savedId ->
                        back()
                        // Segmento novo salvo: abre ele para lançar os ensaios.
                        if (route.id == null && savedId != null) go(Route.SegmentoDetail(savedId))
                    },
                )
                is Route.Ensaio -> when (route.tipo) {
                    TipoEnsaio.IN_SITU -> InSituScreen(current, route.segmentoId, route.id, onDone = back)
                    TipoEnsaio.PROCTOR -> ProctorScreen(current, route.segmentoId, route.id, onDone = back)
                    else -> EmBreveScreen(route.tipo.nome, "Este ensaio entra na versão 0.4.", back)
                }
                Route.Relatorios -> EmBreveScreen("Relatórios gerados", "Os PDFs (padrão do app e padrão Neovia) entram na versão 0.4.", null)
                Route.Resumos -> EmBreveScreen("Últimos resumos", "Os resumos em imagem e texto para WhatsApp entram na versão 0.5.", back)
                Route.CadastrosMenu -> CadastrosMenuScreen(onOpen = { go(Route.Cadastro(it)) })
                is Route.Cadastro -> CadastroListScreen(spec = route.spec, user = current, onBack = back)
            }
        }
        BottomNav(current = route.tab(), onSelect = goTab)
    }
}
