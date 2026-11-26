package bd.bartaguard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bd.bartaguard.engine.Verdict
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// Apple's grouped look in Compose: plain system grey (black in dark mode) with solid cards on it,
// and glass only where iOS uses it, on the bars, which blur what scrolls under them for real on
// Android 12+ (Haze). Colour comes only from the verdict washing the top of the screen.

@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
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
    val thumb: Color,
    val switchOff: Color,
)

private val Light = Palette(
    dark = false, bg = Color(0xFFF2F2F7),
    glass = Color.White, glassStrong = Color.White,
    rimTop = Color.Black.copy(alpha = 0.05f), rimBottom = Color.Black.copy(alpha = 0.05f),
    text = Color(0xFF000000), sub = Color(0xFF3C3C43).copy(alpha = 0.75f), faint = Color(0xFF8E8E93),
    accent = Color(0xFF0A84FF), danger = Color(0xFFFF3B30), warn = Color(0xFFFF9500), info = Color(0xFF0A84FF), ok = Color(0xFF34C759),
    thumb = Color.White, switchOff = Color(0xFFE3E3E8),
)

private val Dark = Palette(
    dark = true, bg = Color(0xFF000000),
    glass = Color(0xFF1C1C1E), glassStrong = Color(0xFF2C2C2E),
    rimTop = Color.White.copy(alpha = 0.08f), rimBottom = Color.White.copy(alpha = 0.03f),
    text = Color(0xFFFFFFFF), sub = Color(0xFFEBEBF5).copy(alpha = 0.6f), faint = Color(0xFF8E8E93),
    accent = Color(0xFF409CFF), danger = Color(0xFFFF453A), warn = Color(0xFFFF9F0A), info = Color(0xFF409CFF), ok = Color(0xFF30D158),
    thumb = Color(0xFF636366), switchOff = Color(0xFF39393D),
)

val LocalPalette = staticCompositionLocalOf { Light }
val P: Palette @Composable get() = LocalPalette.current

/** Switching light/dark cross-fades every colour instead of snapping, like iOS does. */
@Composable
fun GlassTheme(dark: Boolean, content: @Composable () -> Unit) {
    val t = if (dark) Dark else Light
    @Composable fun a(c: Color, l: String) = animateColorAsState(c, tween(500), label = l).value
    val p = Palette(
        dark = dark, bg = a(t.bg, "bg"),
        glass = a(t.glass, "glass"), glassStrong = a(t.glassStrong, "glassStrong"), rimTop = a(t.rimTop, "rimTop"), rimBottom = a(t.rimBottom, "rimBottom"),
        text = a(t.text, "text"), sub = a(t.sub, "sub"), faint = a(t.faint, "faint"), accent = a(t.accent, "accent"),
        danger = a(t.danger, "danger"), warn = a(t.warn, "warn"), info = a(t.info, "info"), ok = a(t.ok, "ok"),
        thumb = a(t.thumb, "thumb"), switchOff = a(t.switchOff, "switchOff"),
    )
    CompositionLocalProvider(LocalPalette provides p, content = content)
}

fun Palette.verdictColor(v: Verdict) = when (v) {
    Verdict.SUSPICIOUS -> danger
    Verdict.UNCERTAIN -> warn
    Verdict.PROMOTIONAL -> info
    Verdict.NORMAL -> ok
}

/**
 * The plain system background. A verdict washes the top of it in red, orange, blue or green, so
 * colour on screen always means something.
 */
@Composable
fun GlassBackdrop(tint: Color? = null, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = P
    val wash by animateColorAsState(tint ?: Color.Transparent, tween(700), label = "wash")
    Box(modifier.fillMaxSize().background(p.bg).drawBehind {
        if (wash.alpha > 0f) drawRect(Brush.verticalGradient(
            listOf(wash.copy(alpha = wash.alpha * if (p.dark) 0.28f else 0.16f), Color.Transparent), endY = size.height * 0.55f))
    }) { content() }
}

/** Frosted glass for the bars: what scrolls underneath is blurred for real. */
fun barStyle(p: Palette) = HazeStyle(
    backgroundColor = p.bg,
    tint = HazeTint(if (p.dark) Color(0xFF161618).copy(alpha = 0.72f) else Color(0xFFF9F9F9).copy(alpha = 0.78f)),
    blurRadius = 26.dp,
    noiseFactor = 0.05f,
)

/**
 * The glass surface. [tint] washes it with a colour (the verdict); [haze] swaps the flat frost for
 * a real backdrop blur. The sheen and rim are drawn from the phone's tilt.
 */
fun Modifier.glass(
    p: Palette, shape: Shape = RoundedCornerShape(28.dp), strong: Boolean = false, elevation: Dp = 18.dp,
    tint: Color? = null, haze: HazeState? = null,
): Modifier = this
    .outerShadow(shape, if (p.dark) 0.dp else elevation * 0.5f, Color.Black.copy(alpha = 0.06f))
    .clip(shape)
    .then(if (haze != null) Modifier.hazeEffect(haze, barStyle(p)) else Modifier.background(if (strong) p.glassStrong else p.glass))
    .then(if (tint != null) Modifier.background(tint.copy(alpha = if (p.dark) 0.16f else 0.10f)) else Modifier)
    .drawWithCache {
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val stroke = Stroke(2.dp.toPx()) // half of it is clipped away: a crisp 1 dp rim
        onDrawWithContent {
            val tl = Tilt.value
            val w = size.width; val h = size.height
            drawRect(Brush.linearGradient(
                listOf(Color.White.copy(alpha = if (p.dark) 0.04f else 0f), Color.Transparent),
                start = Offset(w * (0.1f - tl.x * 0.4f), -h * 0.1f), end = Offset(w * (0.45f - tl.x * 0.4f), h * (0.75f + tl.y * 0.2f)),
            ))
            drawContent()
            drawPath(outline, Brush.linearGradient(
                listOf(p.rimTop, p.rimBottom, p.rimTop.copy(alpha = p.rimTop.alpha * 0.45f)),
                start = Offset(w * (0.3f - tl.x * 0.5f), 0f), end = Offset(w * (0.7f - tl.x * 0.5f), h),
            ), style = stroke)
        }
    }

/**
 * A shadow drawn only outside the shape. Material elevation shadows show through translucent
 * glass as a grey band; real glass doesn't do that.
 */
fun Modifier.outerShadow(shape: Shape, elevation: Dp, color: Color) = drawBehind {
    if (elevation <= 0.dp) return@drawBehind
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
    clipPath(path, ClipOp.Difference) {
        drawIntoCanvas { c ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.BLACK
                setShadowLayer(elevation.toPx(), 0f, elevation.toPx() / 2.5f, color.toArgb())
            }
            c.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier, strong: Boolean = false, padding: Dp = 20.dp, tint: Color? = null,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val m = if (onClick != null) modifier.pressable(scale = 0.97f, onClick = onClick) else modifier
    Column(m.fillMaxWidth().glass(P, strong = strong, tint = tint).padding(padding), content = content)
}

/** iOS 26 glass button: squishes on press and lights up from inside. */
@Composable
fun GlassButton(
    label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, prominent: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    val p = P
    val shape = CircleShape
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val glow by animateFloatAsState(if (pressed && enabled) 1f else 0f, if (pressed) Springs.press() else tween(350), label = "glow")
    val fade by animateFloatAsState(if (enabled) 1f else 0.45f, tween(250), label = "enabled")
    val base = if (prominent) Modifier
        .shadow(14.dp, shape, spotColor = p.accent.copy(alpha = 0.5f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(lerp(p.accent, Color.White, 0.12f), p.accent)))
        .drawWithCache {
            val o = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            onDrawWithContent {
                drawContent()
                drawPath(o, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.05f))), style = Stroke(2.dp.toPx()))
            }
        }
    else Modifier.glass(p, shape, elevation = 8.dp)
    Row(
        modifier.pressable(enabled, 0.95f, src) { onClick() }.graphicsLayer { alpha = fade }.then(base)
            .drawWithCache { onDrawWithContent { drawContent(); if (glow > 0f) drawRect(Color.White.copy(alpha = glow * if (p.dark) 0.14f else 0.22f)) } }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (prominent) Color.White else p.text
        icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(20.dp)) }
        if (icon != null) Box(Modifier.size(8.dp))
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Floating capsule tab bar, iOS 26 style. The selection is a drop of glass that slides between tabs;
 * its leading edge moves first and the trailing edge catches up, so it stretches as it goes.
 */
@Composable
fun GlassTabBar(items: List<Pair<ImageVector, String>>, selected: Int, haze: HazeState, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = P
    val view = LocalView.current
    BoxWithConstraints(modifier.glass(p, CircleShape, elevation = 24.dp, haze = haze).padding(5.dp)) {
        val w = maxWidth / items.size
        var prev by remember { mutableIntStateOf(selected) }
        val right = selected >= prev
        val lead = spring<Dp>(dampingRatio = 0.72f, stiffness = 900f)
        val trail = spring<Dp>(dampingRatio = 0.68f, stiffness = 240f)
        val l by animateDpAsState(w * selected, if (right) trail else lead, label = "l")
        val r by animateDpAsState(w * (selected + 1), if (right) lead else trail, label = "r")
        SideEffect { prev = selected }
        Box(
            Modifier.offset(x = l).width(r - l).height(54.dp).clip(CircleShape)
                .background(if (p.dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.06f))
        )
        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, (icon, label) ->
                val on = i == selected
                val color by animateColorAsState(if (on) p.accent else p.text, tween(200), label = "tabc")
                val bounce = remember { Animatable(1f) }
                LaunchedEffect(on) {
                    if (on) { bounce.snapTo(0.72f); bounce.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f)) }
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .pressable(scale = 0.88f, haptic = null) { if (!on) view.haptic(Haptic.Tap); onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, label, tint = color, modifier = Modifier.size(25.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value })
                    Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** iOS segmented control: the thumb slides over with a spring. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val p = P
    val view = LocalView.current
    // iOS segmented control: a grey track (tertiary fill) with a raised thumb.
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(if (p.dark) Color(0xFF767680).copy(alpha = 0.24f) else Color(0xFF767680).copy(alpha = 0.12f)).padding(4.dp)) {
        val w = maxWidth / options.size
        val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        val x by animateDpAsState(w * idx, Springs.bouncy(), label = "seg")
        Box(Modifier.offset(x = x).width(w).height(40.dp).shadow(4.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(p.thumb))
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (k, label) ->
                Box(
                    Modifier.weight(1f).height(40.dp).pressable(scale = 0.94f, haptic = null) {
                        if (k != selected) { view.haptic(Haptic.Toggle); onSelect(k) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = p.text, fontWeight = if (k == selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 15.sp)
                }
            }
        }
    }
}

/** iOS switch: green track, white knob that stretches while held and springs across. */
@Composable
fun IosSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val p = P
    val view = LocalView.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val progress by animateFloatAsState(if (on) 1f else 0f, Springs.bouncy(), label = "sw")
    val stretch by animateDpAsState(if (pressed) 7.dp else 0.dp, Springs.smooth(), label = "knob")
    val frac = progress.coerceIn(0f, 1f)
    Box(
        Modifier.size(width = 51.dp, height = 31.dp).clip(CircleShape).background(lerp(p.switchOff, p.ok, frac))
            .clickable(src, null) { view.haptic(Haptic.Toggle); onChange(!on) }.padding(2.dp)
    ) {
        val knob = 27.dp + stretch
        Box(
            Modifier.offset(x = (47.dp - knob) * progress).size(width = knob, height = 27.dp)
                .shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White)
        )
    }
}

@Composable
fun LargeTitle(text: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 4.dp, bottom = 4.dp)) {
        Text(text, style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 41.sp), color = P.text)
        sub?.let { Text(it, color = P.sub, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp)) }
    }
}

/** Stretches from its leading edge when the page is pulled down, like an iOS large title. */
fun Modifier.titleStretch(pull: () -> Float) = graphicsLayer {
    val s = 1f + (pull() / 2600f).coerceIn(0f, 0.09f)
    scaleX = s; scaleY = s
    transformOrigin = TransformOrigin(0f, 0.5f)
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = P.faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp))
}
