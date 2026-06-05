package com.oppowatch.aichat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =============================================================================
// 🎨 Liquid Glass · 液态玻璃色板
// =============================================================================

// ── 品牌主色 · 紫色系 ──
val Purple500 = Color(0xFF7850FF)
val Purple400 = Color(0xFF9B7AFF)
val Purple300 = Color(0xFFBBA3FF)
val Purple200 = Color(0xFFD4C4FF)
val Purple600 = Color(0xFF5E3DD4)
val Purple700 = Color(0xFF482BAA)

// ── 深色底板 ──
val DeepBackground = Color(0xFF0A0A18)
val DeepSurface = Color(0xFF111128)
val DeepSurfaceVariant = Color(0xFF1A1A36)

// ── 液态玻璃卡片背景 (半透) ──
// 底层实际为深色，叠加半透白/紫产生玻璃质感
val GlassCard = Color(0x1AFFFFFF)   // 10% 白 → 深底上微亮玻璃
val GlassCardAlt = Color(0x1E7850FF) // 12% 紫 → 紫色调玻璃
val GlassBorder = Color(0x26FFFFFF)  // 15% 白 → 玻璃边缘
val GlassHighlight = Color(0x0DFFFFFF) // 5% 白高光

// ── 文字色 ──
val TextPrimary = Color(0xFFEEEEF2)
val TextSecondary = Color(0xFFB0B0C0)
val TextTertiary = Color(0xFF6E6E80)

// ── 功能性色 ──
val Success = Color(0xFF4ADE80)
val Warning = Color(0xFFFBBF24)
val Error = Color(0xFFF87171)
val Info = Color(0xFF60A5FA)

// =============================================================================
// 🌙 Material3 Dark Color Scheme
// =============================================================================

private val LiquidGlassDarkColors = darkColorScheme(
    // 主色
    primary = Purple500,
    onPrimary = Color.White,
    primaryContainer = Purple700,
    onPrimaryContainer = Purple200,

    // 次要色
    secondary = Purple400,
    onSecondary = Color.White,
    secondaryContainer = Purple600,
    onSecondaryContainer = Purple200,

    // 三级色
    tertiary = Info,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF1E3A5F),
    onTertiaryContainer = Color(0xFFBBDEFB),

    // 背景 / 表面
    background = DeepBackground,
    onBackground = TextPrimary,
    surface = DeepSurface,
    onSurface = TextPrimary,
    surfaceVariant = DeepSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    // 轮廓
    outline = GlassBorder,
    outlineVariant = Color(0x14FFFFFF),

    // 错误
    error = Error,
    onError = Color.Black,
    errorContainer = Color(0xFF3B1E1E),
    onErrorContainer = Color(0xFFFFCDD2),

    // 反色
    inverseSurface = Color(0xFFE0E0F0),
    inverseOnSurface = Color(0xFF1A1A2E),
    inversePrimary = Purple700,

    // Scrim
    scrim = Color(0xAA000000),
)

// =============================================================================
// 🔤 Compact Typography · 紧凑排版（适配手表小屏）
// =============================================================================

/**
 * 手表屏幕极有限（约 1.6-1.9 英寸），需要极度紧凑的字体层级。
 * 基线参考：Display > Headline > Title > Body > Label
 * 同时避免过小字号影响可读性，最小不低于 9sp。
 */

val CompactTypography = Typography(
    // 大标题 · 页面主标题（如 "AI 对话"）
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
    ),

    // 标题
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 17.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),

    // 标题栏 · TopAppBar 等
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.1.sp,
    ),

    // 正文
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.25.sp,
    ),

    // 标签 / 按钮文字
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.5.sp,
    ),
)

// =============================================================================
// 🔷 Shapes · 圆角（配合玻璃质感使用较大圆角）
// =============================================================================

val GlassShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

// =============================================================================
// 🌓 Glass Effect Colors · 运行时玻璃色（CompositionLocal 注入）
// =============================================================================

data class GlassColors(
    /** 玻璃卡片背景 */
    val cardBackground: Color = GlassCard,
    /** 玻璃卡片备选背景 */
    val cardBackgroundAlt: Color = GlassCardAlt,
    /** 玻璃边框 */
    val border: Color = GlassBorder,
    /** 玻璃高光 */
    val highlight: Color = GlassHighlight,
    /** 深底 */
    val deepBackground: Color = DeepBackground,
)

val LocalGlassColors = staticCompositionLocalOf { GlassColors() }

// =============================================================================
// 🧩 Theme · 主题入口
// =============================================================================

@Composable
fun OppoWatchAITheme(
    // 手表上固定深色；保留参数以便未来扩展
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = LiquidGlassDarkColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CompactTypography,
        shapes = GlassShapes,
    ) {
        CompositionLocalProvider(
            LocalGlassColors provides GlassColors(),
        ) {
            content()
        }
    }
}

// =============================================================================
// 🔧 便捷访问器
// =============================================================================

/** 从 CompositionLocal 取出玻璃色 */
object OppoWatchTheme {
    val glassColors: GlassColors
        @Composable get() = LocalGlassColors.current
}
