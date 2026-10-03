package br.ensaios.cliente.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ensaios.cliente.data.AppSettings
import br.ensaios.cliente.ui.theme.appColors

/** Abas da barra de baixo. */
enum class Tab(val label: String, val icon: ImageVector) {
    INICIO("Início", Icons.Filled.Home),
    PRODUCAO("Produção", Icons.Filled.DateRange),
    RELATORIOS("Relatórios", Icons.Filled.Share),
    CADASTROS("Cadastros", Icons.Filled.List),
}

/** Estrutura padrão das telas: barra colorida no topo, conteúdo e (opcional) barra de abas. */
@Composable
fun AppScreen(
    title: String,
    /** Ícone desenhado ao lado do título (ex.: tipo de ensaio). */
    icon: Int? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    fab: (() -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = appColors
    val context = LocalContext.current
    SideEffect {
        (context as? Activity)?.window?.statusBarColor = c.header.toArgb()
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.header)
                .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = c.headerInk)
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                if (icon != null) {
                    Icon(painterResource(icon), contentDescription = null, tint = c.headerInk, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    color = c.headerInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                actions()
                SunToggle()
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = c.headerInk.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = if (onBack != null) 48.dp else 8.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val base = Modifier.fillMaxSize().padding(horizontal = 12.dp)
            Column(
                if (scroll) base.verticalScroll(rememberScrollState()) else base,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Spacer(Modifier.height(2.dp))
                content()
                Spacer(Modifier.height(if (fab != null) 88.dp else 16.dp))
            }
            if (fab != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.primary)
                        .clickable(onClick = fab),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        bottomBar()
    }
}

/** Botão de sol forte (alto contraste), presente em todas as telas. */
@Composable
fun SunToggle() {
    val context = LocalContext.current
    val on by AppSettings.solForte.collectAsState()
    val c = appColors
    Box(
        Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) Color(0xFFFFD54F) else c.headerInk.copy(alpha = 0.15f))
            .clickable { AppSettings.toggleSolForte(context) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("☀", fontSize = 18.sp, color = if (on) Color.Black else c.headerInk)
    }
}

/** Ação de texto na barra do topo (ex.: "Salvar"). */
@Composable
fun HeaderAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = appColors.headerInk,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(10.dp),
    )
}

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    val c = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(BorderStroke(1.dp, c.line))
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Tab.values().forEach { t ->
            val on = t == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(t) }.padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) c.primarySoft else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                ) {
                    Icon(t.icon, contentDescription = null, tint = if (on) c.primary else c.muted)
                }
                Text(t.label, fontSize = 11.sp, color = if (on) c.primary else c.muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Cartão branco com borda leve. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: Color? = null,
    background: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = appColors
    Surface(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(14.dp),
        color = background ?: c.surface,
        border = BorderStroke(if (border != null) 2.dp else 1.dp, border ?: c.line),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

/** Botão grande da tela inicial. */
@Composable
fun Tile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Tile(rememberVectorPainter(icon), title, subtitle, modifier, onClick)

/** Botão grande com ícone desenhado (ex.: ícones dos ensaios). */
@Composable
fun Tile(icon: Painter, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = appColors
    AppCard(modifier = modifier, onClick = onClick) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(c.primarySoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
        Text(subtitle, fontSize = 12.sp, color = c.muted)
    }
}

enum class PillKind { OK, BAD, WARN, NEUTRAL, PRIMARY }

@Composable
fun Pill(text: String, kind: PillKind) {
    val c = appColors
    val (bg, fg) = when (kind) {
        PillKind.OK -> c.okSoft to c.ok
        PillKind.BAD -> c.badSoft to c.bad
        PillKind.WARN -> c.warnSoft to c.warn
        PillKind.PRIMARY -> c.primarySoft to c.primary
        PillKind.NEUTRAL -> c.background to c.muted
    }
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Título de bloco (ex.: "POSIÇÃO"). */
@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = appColors.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp),
    )
}

/** Indicador com número grande. */
@Composable
fun Kpi(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    val c = appColors
    AppCard(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = c.muted)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
        Text(sub, fontSize = 12.sp, color = c.muted)
    }
}

/** Selo colorido à esquerda dos cartões (posição km, tipo de ensaio). */
@Composable
fun Badge(top: String, bottom: String, kind: PillKind = PillKind.PRIMARY) {
    val c = appColors
    val (bg, fg) = when (kind) {
        PillKind.OK -> c.okSoft to c.ok
        PillKind.BAD -> c.badSoft to c.bad
        PillKind.WARN -> c.warnSoft to c.warn
        else -> c.primarySoft to c.primary
    }
    Column(
        Modifier.widthIn(min = 84.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(top, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
        Text(bottom, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** Selo do ensaio: ícone do tipo e a posição. */
@Composable
fun EnsaioBadge(icon: Painter, bottom: String, kind: PillKind) {
    val c = appColors
    val (bg, fg) = when (kind) {
        PillKind.OK -> c.okSoft to c.ok
        PillKind.BAD -> c.badSoft to c.bad
        PillKind.WARN -> c.warnSoft to c.warn
        else -> c.primarySoft to c.primary
    }
    Column(
        Modifier.widthIn(min = 84.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
        Text(bottom, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** Quadro de resultado calculado (ex.: Extensão 420 m). */
@Composable
fun ResultBox(items: List<Pair<String, String>>) {
    val c = appColors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.primarySoft).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEachIndexed { i, (label, value) ->
            Column(horizontalAlignment = if (i == 0) Alignment.Start else if (i == items.lastIndex) Alignment.End else Alignment.CenterHorizontally) {
                Text(label, fontSize = 12.sp, color = c.muted)
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.primary)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = appColors.primary),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, color: Color? = null, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold, color = color ?: appColors.primary) }
}

/** Linha "rótulo: valor" dos detalhes. */
@Composable
fun InfoRow(label: String, value: String) {
    if (value.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = appColors.muted, modifier = Modifier.weight(0.45f), fontSize = 14.sp)
        Text(value, color = appColors.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.55f), fontSize = 14.sp)
    }
}

@Composable
fun EmptyText(text: String) {
    Text(text, color = appColors.muted, modifier = Modifier.padding(8.dp))
}

@Composable
fun MutedText(text: String) {
    Text(text, color = appColors.muted, fontSize = 13.sp)
}

@Composable
fun TitleText(text: String) {
    Text(text, color = appColors.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
}
