package top.jlen.vod.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object UiPalette {
    private data class PaletteColors(
        val backgroundTop: Color,
        val backgroundBottom: Color,
        val surface: Color,
        val surfaceStrong: Color,
        val surfaceSoft: Color,
        val border: Color,
        val borderSoft: Color,
        val accent: Color,
        val accentSoft: Color,
        val accentText: Color,
        val accentGlow: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val textMuted: Color,
        val ink: Color,
        val heroStart: Color,
        val heroEnd: Color,
        val playerBackground: Color,
        val dangerSurface: Color,
        val dangerBorder: Color,
        val dangerText: Color
    )

    private val lightPalette = PaletteColors(
        backgroundTop = Color(0xFFF8F9FB),
        backgroundBottom = Color(0xFFF1F4F8),
        surface = Color(0xFFFFFFFF),
        surfaceStrong = Color(0xFFF5F5F5),
        surfaceSoft = Color(0xFFF3F5F7),
        border = Color(0xFFE7EAF0),
        borderSoft = Color(0xFFD8DDE6),
        accent = Color(0xFFFF2A14),
        accentSoft = Color(0xFFFF9A4D),
        accentText = Color(0xFFFFFFFF),
        accentGlow = Color(0xFFFFE3DE),
        textPrimary = Color(0xFF1C1C22),
        textSecondary = Color(0xFF5F6673),
        textMuted = Color(0xFF9EA5B1),
        ink = Color(0xFF25252B),
        heroStart = Color(0xFFB9E5FF),
        heroEnd = Color(0xFFF7FBFF),
        playerBackground = Color(0xFF121212),
        dangerSurface = Color(0xFFFFF0ED),
        dangerBorder = Color(0xFFFFC9BE),
        dangerText = Color(0xFF9F2D20)
    )

    private val darkPalette = PaletteColors(
        backgroundTop = Color(0xFF121419),
        backgroundBottom = Color(0xFF0B0D11),
        surface = Color(0xFF181C22),
        surfaceStrong = Color(0xFF20252D),
        surfaceSoft = Color(0xFF14181E),
        border = Color(0xFF2B313B),
        borderSoft = Color(0xFF363D48),
        accent = Color(0xFFFF5A3D),
        accentSoft = Color(0xFFFF9E66),
        accentText = Color(0xFFFFFFFF),
        accentGlow = Color(0x40FF7A59),
        textPrimary = Color(0xFFF5F7FA),
        textSecondary = Color(0xFFBDC5D0),
        textMuted = Color(0xFF8B95A3),
        ink = Color(0xFFF6F7F9),
        heroStart = Color(0xFF1A2430),
        heroEnd = Color(0xFF0F131A),
        playerBackground = Color(0xFF090B0F),
        dangerSurface = Color(0xFF2A1715),
        dangerBorder = Color(0xFF6A3B34),
        dangerText = Color(0xFFFFB6A8)
    )

    private var isDarkMode by mutableStateOf(false)

    fun syncWithSystem(isDarkTheme: Boolean) {
        isDarkMode = isDarkTheme
    }

    private val colors: PaletteColors
        get() = if (isDarkMode) darkPalette else lightPalette

    val BackgroundTop: Color get() = colors.backgroundTop
    val BackgroundBottom: Color get() = colors.backgroundBottom
    val Surface: Color get() = colors.surface
    val SurfaceStrong: Color get() = colors.surfaceStrong
    val SurfaceSoft: Color get() = colors.surfaceSoft
    val Border: Color get() = colors.border
    val BorderSoft: Color get() = colors.borderSoft

    val Accent: Color get() = colors.accent
    val AccentSoft: Color get() = colors.accentSoft
    val AccentText: Color get() = colors.accentText
    val AccentGlow: Color get() = colors.accentGlow

    val TextPrimary: Color get() = colors.textPrimary
    val TextSecondary: Color get() = colors.textSecondary
    val TextMuted: Color get() = colors.textMuted
    val Ink: Color get() = colors.ink

    val HeroStart: Color get() = colors.heroStart
    val HeroEnd: Color get() = colors.heroEnd
    val PlayerBackground: Color get() = colors.playerBackground

    val DangerSurface: Color get() = colors.dangerSurface
    val DangerBorder: Color get() = colors.dangerBorder
    val DangerText: Color get() = colors.dangerText
}
