package bd.bartaguard.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// The iOS feel, ported: springs instead of easing curves, things that squish when pressed and
// settle with a small overshoot, lists that rubber-band at the edges, and glass that catches the
// light as the phone tilts.

object Springs {
    /** Most UI motion: quick, with a hint of overshoot (SwiftUI's default .spring). */
    fun <T> smooth() = spring<T>(dampingRatio = 0.86f, stiffness = 320f)
    /** Things that should feel alive: icons popping in, the tab indicator, switches. */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.58f, stiffness = 380f)
    /** Press-down: fast and flat, so a tap feels immediate. */
    fun <T> press() = spring<T>(dampingRatio = 1f, stiffness = 1400f)
    /** Release: springs back past 1.0 a little. */
    fun <T> release() = spring<T>(dampingRatio = 0.45f, stiffness = 420f)
}

enum class Haptic { Tap, Toggle, Success, Warning }

fun View.haptic(kind: Haptic) {
    val c = when (kind) {
        Haptic.Tap -> HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.Toggle -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
        Haptic.Success -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.Warning -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(c)
}

/** Squishes while held, springs back on release, taps the haptic engine on click. */
fun Modifier.pressable(
    enabled: Boolean = true,
    scale: Float = 0.96f,
    interaction: MutableInteractionSource? = null,
    haptic: Haptic? = Haptic.Tap,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interaction ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) scale else 1f, if (pressed) Springs.press() else Springs.release(), label = "press")
    val view = LocalView.current
    graphicsLayer { scaleX = s; scaleY = s }
        .clickable(source, null, enabled) { haptic?.let(view::haptic); onClick() }
}

/**
 * Slides up and fades in with a spring the first time it's shown. [index] staggers a list so the
 * cards cascade in one after another, the way iOS reveals a result sheet.
 */
fun Modifier.appear(index: Int = 0, key: Any? = Unit, distance: Float = 36f): Modifier = composed {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(index * 60L)
        a.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 210f))
    }
    graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * distance.dp.toPx()
        val sc = 0.94f + 0.06f * v
        scaleX = sc; scaleY = sc
    }
}

/**
 * iOS rubber-band scrolling for a vertically scrolling child. Pulling past an edge stretches the
 * content with growing resistance; letting go (or flinging into an edge) springs it back.
 * Android's own stretch overscroll is switched off where this is used.
 */
class RubberBand(private val scope: CoroutineScope) : NestedScrollConnection {
    var offset by mutableFloatStateOf(0f)
        private set
    private var settle: Job? = null

    private fun resist(delta: Float): Float {
        val k = 1f - (abs(offset) / 900f).coerceAtMost(0.85f)
        return delta * 0.5f * k
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (offset == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        // Pulling back towards rest eats the stretch first.
        if ((offset > 0f && available.y < 0f) || (offset < 0f && available.y > 0f)) {
            settle?.cancel()
            val next = offset + available.y
            val used = if ((offset > 0f) == (next > 0f)) available.y else -offset
            offset = if ((offset > 0f) == (next > 0f)) next else 0f
            return Offset(0f, used)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        settle?.cancel()
        offset += resist(available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (offset == 0f) return Velocity.Zero
        springBack(available.y)
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // A fling that hits the edge: carry its momentum into a small overshoot and back.
        if (abs(available.y) > 50f) springBack(available.y * 0.35f)
        return available
    }

    private fun springBack(velocity: Float) {
        settle?.cancel()
        settle = scope.launch {
            animate(offset, 0f, velocity, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> offset = v }
        }
    }
}

@androidx.compose.runtime.Composable
fun rememberRubberBand(): RubberBand {
    val scope = rememberCoroutineScope()
    return remember { RubberBand(scope) }
}

/**
 * Device tilt, smoothed, in -1..1. The glass rims and the colour field read it while drawing, so
 * tilting the phone moves the highlights without recomposing anything.
 */
object Tilt : SensorEventListener {
    var value by mutableStateOf(Offset.Zero)
        private set
    private var sm: SensorManager? = null
    private var x = 0f
    private var y = 0f
    private var baseY = Float.NaN

    fun start(ctx: Context) {
        val m = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val s = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm = m
        m.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() { sm?.unregisterListener(this); sm = null }

    override fun onSensorChanged(e: SensorEvent) {
        val gx = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
        val gy = (e.values[1] / 9.81f).coerceIn(-1f, 1f)
        // However the phone is held is "level"; only changes from that move the light.
        baseY = if (baseY.isNaN()) gy else baseY * 0.995f + gy * 0.005f
        x = x * 0.88f + gx * 0.12f
        y = y * 0.88f + (gy - baseY) * 2f * 0.12f
        val next = Offset(x, y.coerceIn(-1f, 1f))
        if ((next - value).getDistance() > 0.004f) value = next
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
