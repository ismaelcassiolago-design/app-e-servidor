package br.ensaios.servidor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Paletas de cor que o usuário escolhe nas configurações. */
enum class Palette(val label: String) {
    VERDE("Verde campo"),
    LARANJA("Laranja obra"),
    VERMELHO("Vermelho e grafite"),
    SOL_FORTE("Sol forte (alto contraste)"),
}

data class AppColors(
    val primary: Color,
    val primarySoft: Color,
    val header: Color,
    val headerInk: Color = Color.White,
    val background: Color = Color(0xFFEEF1F4),
    val surface: Color = Color.White,
    val ink: Color = Color(0xFF16202A),
    val muted: Color = Color(0xFF5F6B78),
    val line: Color = Color(0xFFE3E7EC),
    val ok: Color = Color(0xFF1F8A4C),
    val okSoft: Color = Color(0xFFE2F4E9),
    val bad: Color = Color(0xFFC62828),
    val badSoft: Color = Color(0xFFFBE5E5),
    val warn: Color = Color(0xFFB26A00),
    val warnSoft: Color = Color(0xFFFFF1D9),
    val fontScale: Float = 1f,
)

fun colorsFor(p: Palette): AppColors = when (p) {
    Palette.VERDE -> AppColors(primary = Color(0xFF1F6F43), primarySoft = Color(0xFFE3F2E8), header = Color(0xFF1F6F43))
    Palette.LARANJA -> AppColors(primary = Color(0xFFE8710A), primarySoft = Color(0xFFFFF0E0), header = Color(0xFF23303D))
    Palette.VERMELHO -> AppColors(primary = Color(0xFFC8102E), primarySoft = Color(0xFFFDE8EB), header = Color(0xFF2B2B2E))
    Palette.SOL_FORTE -> AppColors(
        primary = Color(0xFF003F8A), primarySoft = Color(0xFFDCE8FF), header = Color.Black,
        background = Color.White, ink = Color.Black, muted = Color(0xFF222222), line = Color(0xFF555555),
        ok = Color(0xFF00602A), okSoft = Color(0xFFC8F0D4), bad = Color(0xFFA00000), badSoft = Color(0xFFFFD0D0),
        warn = Color(0xFF7A4400), warnSoft = Color(0xFFFFE2B0), fontScale = 1.15f,
    )
}

val LocalAppColors = staticCompositionLocalOf { colorsFor(Palette.VERDE) }

/** Atalho: cores do tema atual. */
val appColors: AppColors
    @Composable get() = LocalAppColors.current

@Composable
fun AppTheme(palette: Palette, content: @Composable () -> Unit) {
    val c = colorsFor(palette)
    val scheme = lightColorScheme(
        primary = c.primary,
        onPrimary = Color.White,
        primaryContainer = c.primarySoft,
        onPrimaryContainer = c.primary,
        secondary = c.header,
        background = c.background,
        surface = c.surface,
        onSurface = c.ink,
        onSurfaceVariant = c.muted,
        outline = c.line,
        error = c.bad,
    )
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalAppColors provides c,
        LocalDensity provides Density(density.density, density.fontScale * c.fontScale),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
