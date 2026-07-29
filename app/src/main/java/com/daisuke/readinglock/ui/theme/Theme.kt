package com.daisuke.readinglock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * ハンドオフの配色をそのまま名前付きロールにしたもの。
 * Material3 の ColorScheme には収まらない語彙(しおりの赤・リングのトラック等)が多いので
 * 独自パレットを CompositionLocal で配り、MaterialTheme は最低限だけ合わせる。
 */
@Immutable
data class RlColors(
    val bg: Color,
    val surface: Color,
    val surfaceBorder: Color,
    /** 主役の面(ライト=インクネイビー / ダーク=生成り) */
    val primary: Color,
    val onPrimary: Color,
    val heading: Color,
    val secondary: Color,
    val muted: Color,
    val faint: Color,
    val dashed: Color,
    val red: Color,
    val onRed: Color,
    val ringTrack: Color,
    val achievedDay: Color,
    val achievedDayInk: Color,
    val tabTrack: Color,
    val amberBg: Color,
    val amberBorder: Color,
    val amberInk: Color,
    val amberSub: Color,
    val green: Color,
    val dotInactive: Color,
    val isDark: Boolean,
) {
    /** トーナルボタン・状態バッジの下地 rgba(46,64,87,.09) */
    val tonal: Color get() = primary.copy(alpha = if (isDark) 0.10f else 0.09f)
}

private val Light = RlColors(
    bg = Color(0xFFF5F0E4),
    surface = Color(0xFFFDFBF6),
    surfaceBorder = Color(0xFFE8E0CC),
    primary = Color(0xFF2E4057),
    onPrimary = Color(0xFFFBF6EA),
    heading = Color(0xFF20293B),
    secondary = Color(0xFF5C5647),
    muted = Color(0xFF8B8270),
    faint = Color(0xFFB4AB95),
    dashed = Color(0xFFC9BFA4),
    red = Color(0xFFA6453C),
    onRed = Color(0xFFFBF6EA),
    ringTrack = Color(0xFFE8DFC9),
    achievedDay = Color(0xFFF0DFDA),
    achievedDayInk = Color(0xFFA6453C),
    tabTrack = Color(0xFFEDE6D3),
    amberBg = Color(0xFFF3E8D2),
    amberBorder = Color(0xFFDCC488),
    amberInk = Color(0xFF5C4310),
    amberSub = Color(0xFF7A5A18),
    green = Color(0xFF5A6B4A),
    dotInactive = Color(0xFFD6CCB2),
    isDark = false,
)

private val Dark = RlColors(
    bg = Color(0xFF171C26),
    surface = Color(0xFF202737),
    surfaceBorder = Color(0xFF2E3648),
    primary = Color(0xFFE8DFC9),
    onPrimary = Color(0xFF20293B),
    heading = Color(0xFFEAE5D6),
    secondary = Color(0xFFB8BFCE),
    muted = Color(0xFF8E96A8),
    faint = Color(0xFF6C7488),
    dashed = Color(0xFF3C4759),
    red = Color(0xFFC05A50),
    onRed = Color(0xFFFBF6EA),
    ringTrack = Color(0xFF2C3547),
    achievedDay = Color(0x33C05A50),
    achievedDayInk = Color(0xFFE0A79E),
    tabTrack = Color(0xFF202737),
    amberBg = Color(0xFF3A3016),
    amberBorder = Color(0xFF6A5620),
    amberInk = Color(0xFFEBD9A8),
    amberSub = Color(0xFFC9B078),
    green = Color(0xFF8FA57A),
    dotInactive = Color(0xFF3C4759),
    isDark = true,
)

val LocalRlColors = staticCompositionLocalOf { Light }

/** どの composable からも `rl.red` のように引ける */
val rl: RlColors
    @Composable @ReadOnlyComposable get() = LocalRlColors.current

/**
 * 見出しと大きな数字。Android の FontFamily.Serif は和文で Noto Serif CJK JP に解決されるので
 * 明朝の質感はこれで出る。Shippori Mincho の Downloadable Fonts は GMS 証明書配列
 * (com_google_android_gms_fonts_certs)を自前でリソース同梱する必要があり、
 * バイナリ非同梱の方針と噛み合わないため採用しない。
 */
val Mincho = FontFamily.Serif

@Composable
fun ReadingLockTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.bg,
            surface = colors.surface,
            onSurface = colors.heading,
            error = colors.red,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.bg,
            surface = colors.surface,
            onSurface = colors.heading,
            error = colors.red,
        )
    }
    CompositionLocalProvider(LocalRlColors provides colors) {
        MaterialTheme(colorScheme = scheme) {
            Surface(color = colors.bg, contentColor = colors.heading, content = content)
        }
    }
}
