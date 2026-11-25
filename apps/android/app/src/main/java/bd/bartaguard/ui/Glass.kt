package bd.bartaguard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bd.bartaguard.engine.Verdict

// "Liquid Glass" in Compose: translucent, softly lit layers floating over a slow colour field.
// Real backdrop blur needs RenderEffect on the content behind, which Compose can't sample cheaply,
// so the glass is a frosted tint + specular rim + soft shadow. It reads the same and costs nothing.

@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val blobs: List<Color>,
    val glass: Color,
    val glassStrong: Color,
    val rimTop: Color,
    val rimBottom: Color,
    val text: Color,
    val sub: Color,
    val faint: Color,
    val accent: Color,
    val danger: Color,
    val warn: Color,
    val info: Color,
    val ok: Color,
)

private val Light = Palette(
    dark = false, bg = Color(0xFFEFF2F8),
    blobs = listOf(Color(0xFF9DC4FF), Color(0xFFFFC7A8), Color(0xFFB9F1D9), Color(0xFFD9C4FF)),
    glass = Color.White.copy(alpha = 0.55f), glassStrong = Color.White.copy(alpha = 0.78f),
    rimTop = Color.White.copy(alpha = 0.95f), rimBottom = Color.White.copy(alpha = 0.25f),
    text = Color(0xFF0B0D12), sub = Color(0xFF4A505C), faint = Color(0xFF8A90A0),
    accent = Color(0xFF0A84FF), danger = Color(0xFFFF3B30), warn = Color(0xFFFF9500), info = Color(0xFF0A84FF), ok = Color(0xFF28B463),
)

private val Dark = Palette(
    dark = true, bg = Color(0xFF07090F),
    blobs = listOf(Color(0xFF1E4DB7), Color(0xFF7A2F6B), Color(0xFF0F6E5C), Color(0xFF4B2FA8)),
    glass = Color.White.copy(alpha = 0.09f), glassStrong = Color.White.copy(alpha = 0.16f),
    rimTop = Color.White.copy(alpha = 0.35f), rimBottom = Color.White.copy(alpha = 0.06f),
    text = Color(0xFFF5F7FB), sub = Color(0xFFB6BCC9), faint = Color(0xFF7D8494),
    accent = Color(0xFF409CFF), danger = Color(0xFFFF453A), warn = Color(0xFFFF9F0A), info = Color(0xFF409CFF), ok = Color(0xFF30D158),
)

val LocalPalette = staticCompositionLocalOf { Light }
val P: Palette @Composable get() = LocalPalette.current

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (isSystemInDarkTheme()) Dark else Light, content = content)
}

fun Palette.verdictColor(v: Verdict) = when (v) {
    Verdict.SUSPICIOUS -> danger
    Verdict.UNCERTAIN -> warn
    Verdict.PROMOTIONAL -> info
    Verdict.NORMAL -> ok
}

/** The slow-moving colour field everything floats on. */
@Composable
fun GlassBackdrop(tint: Color? = null, content: @Composable BoxScope.() -> Unit) {
    val p = P
    val t = rememberInfiniteTransition(label = "blobs")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "phase")
    val wash by animateColorAsState(tint ?: Color.Transparent, tween(600), label = "wash")
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val spots = listOf(
                Offset(w * (0.15f + 0.2f * phase), h * 0.12f), Offset(w * (0.95f - 0.15f * phase), h * 0.32f),
                Offset(w * 0.2f, h * (0.62f + 0.1f * phase)), Offset(w * (0.8f + 0.1f * phase), h * 0.88f),
            )
            spots.forEachIndexed { i, c ->
                val r = w * (0.75f + 0.1f * i % 2)
                drawCircle(Brush.radialGradient(listOf(p.blobs[i].copy(alpha = if (p.dark) 0.55f else 0.7f), Color.Transparent), c, r), r, c)
            }
            if (wash.alpha > 0f) {
                drawRect(Brush.verticalGradient(listOf(wash.copy(alpha = if (p.dark) 0.35f else 0.25f), Color.Transparent), endY = h * 0.6f))
            }
        }
        content()
    }
}

fun Modifier.glass(p: Palette, shape: Shape = RoundedCornerShape(28.dp), strong: Boolean = false, elevation: Dp = 18.dp) = this
    .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = if (p.dark) 0.5f else 0.14f))
    .clip(shape)
    .background(if (strong) p.glassStrong else p.glass)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) 0.06f else 0.25f), Color.Transparent)))
    .border(1.dp, Brush.verticalGradient(listOf(p.rimTop, p.rimBottom)), shape)

@Composable
fun GlassCard(modifier: Modifier = Modifier, strong: Boolean = false, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.glass(P, strong = strong).padding(padding), content = content)
}

@Composable
fun GlassButton(
    label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, prominent: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    val p = P
    val shape = CircleShape
    val base = if (prominent) Modifier
        .shadow(14.dp, shape, spotColor = p.accent.copy(alpha = 0.5f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.92f), p.accent)))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f))), shape)
    else Modifier.glass(p, shape, elevation = 8.dp)
    Row(
        modifier.then(base).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (prominent) Color.White else p.text
        icon?.let { Icon(it, null, tint = fg.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(20.dp).padding(end = 0.dp)) }
        if (icon != null) Box(Modifier.size(8.dp))
        Text(label, color = fg.copy(alpha = if (enabled) 1f else 0.4f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Floating capsule tab bar, iOS 26 style. */
@Composable
fun GlassTabBar(items: List<Pair<ImageVector, String>>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = P
    Row(modifier.glass(p, CircleShape, strong = true, elevation = 24.dp).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            val bg by animateColorAsState(if (on) p.accent.copy(alpha = if (p.dark) 0.28f else 0.14f) else Color.Transparent, label = "tab")
            Column(
                Modifier.weight(1f).clip(CircleShape).background(bg)
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, label, tint = if (on) p.accent else p.sub, modifier = Modifier.size(24.dp))
                Text(label, color = if (on) p.accent else p.sub, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
fun LargeTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
        Text(text, style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), color = P.text)
        sub?.let { Text(it, color = P.sub, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp)) }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = P.faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp))
}

@Composable
fun RowScope.Spacer1() = Box(Modifier.weight(1f))
