package bd.bartaguard.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bd.bartaguard.AppViewModel
import bd.bartaguard.WatchService
import bd.bartaguard.ui.GlassButton
import bd.bartaguard.ui.Haptic
import bd.bartaguard.ui.P
import bd.bartaguard.ui.Springs
import bd.bartaguard.ui.Strings
import bd.bartaguard.ui.glass
import bd.bartaguard.ui.haptic
import bd.bartaguard.ui.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sheet that walks someone through turning auto-check on. Each step ticks green on its own
 * when they come back from system settings (the activity refreshes the state on resume).
 */
@Composable
fun SetupSheet(vm: AppViewModel, s: Strings) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val close = { vm.setupOpen = false }
    BackHandler(vm.setupOpen, close)

    // If the system dialog comes back instantly it wasn't shown (asked twice already), so go to
    // the app's notification settings instead.
    val asked = remember { longArrayOf(0L) }
    val alertPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && SystemClock.elapsedRealtime() - asked[0] < 400) openAlertSettings(ctx)
        vm.refreshAccess()
    }

    val steps = buildList {
        add(Step(s.t("Notification access", "নোটিফিকেশন অ্যাক্সেস"),
            s.t("Lets BartaGuard read new message notifications. Nothing leaves your phone.",
                "নতুন মেসেজের নোটিফিকেশন পড়তে দেয়। কিছুই ফোনের বাইরে যায় না।"),
            vm.access, s.t("Open", "খুলুন"),
            if (Build.VERSION.SDK_INT >= 33) s.t("Switch greyed out? App info → ⋮ → Allow restricted settings.",
                "সুইচ ধূসর? অ্যাপ ইনফো → ⋮ → Allow restricted settings।") else null,
        ) { openListenerSettings(ctx) })
        add(Step(s.t("Scam alerts", "সতর্কবার্তা"),
            s.t("Allow notifications so we can warn you.", "নোটিফিকেশনের অনুমতি দিন, যাতে আমরা সতর্ক করতে পারি।"),
            vm.alertsOk, s.t("Allow", "অনুমতি"), null,
        ) {
            WatchService.ensureChannel(ctx, s.bn)
            if (Build.VERSION.SDK_INT >= 33) { asked[0] = SystemClock.elapsedRealtime(); alertPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
            else openAlertSettings(ctx)
        })
        if (vm.needsBattery) add(Step(s.t("Keep running", "চালু রাখুন"),
            s.t("${brand()} phones close apps to save battery. Choose Battery → Unrestricted (or No restrictions).",
                "${brand()} ফোন ব্যাটারি বাঁচাতে অ্যাপ বন্ধ করে দেয়। ব্যাটারি → Unrestricted (বা No restrictions) বেছে নিন।"),
            vm.batteryOk, s.t("Open", "খুলুন"),
            if (Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")) s.t("Also turn on Autostart on the same page.", "একই পাতায় Autostart-ও চালু করুন।") else null,
        ) { openAppInfo(ctx) })
    }
    val done = steps.count { it.done }
    val allDone = done == steps.size

    // Everything granted while the sheet is open: a success tap, then it closes itself.
    LaunchedEffect(allDone, vm.setupOpen) {
        if (allDone && vm.setupOpen) { view.haptic(Haptic.Success); delay(900); close() }
    }
    LaunchedEffect(vm.setupOpen) { if (vm.setupOpen) drag.snapTo(0f) }

    AnimatedVisibility(vm.setupOpen, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = close))
    }
    val dismissAt = with(LocalDensity.current) { 120.dp.toPx() }
    AnimatedVisibility(
        vm.setupOpen,
        enter = slideInVertically(Springs.smooth()) { it },
        exit = slideOutVertically(tween(260)) { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.navigationBarsPadding().padding(8.dp).fillMaxWidth()
                    .graphicsLayer { translationY = drag.value }
                    .draggable(
                        rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
                        Orientation.Vertical,
                        onDragStopped = { v -> if (drag.value > dismissAt || v > 1800f) close() else drag.animateTo(0f, Springs.bouncy()) },
                    )
                    .glass(P, RoundedCornerShape(34.dp), strong = true)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(CircleShape).background(P.faint.copy(alpha = 0.45f)))
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsActive, null, tint = P.accent, modifier = Modifier.size(28.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(s.t("Turn on auto-check", "অটো-যাচাই চালু করুন"), color = P.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(s.t("$done of ${steps.size} done", "${s.num(steps.size)}টির মধ্যে ${s.num(done)}টি হয়েছে"), color = P.sub, fontSize = 14.sp)
                    }
                }
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    steps.forEachIndexed { i, st -> StepRow(i + 1, st, s) }
                }
                GlassButton(
                    if (allDone) s.t("All set", "সব ঠিক আছে") else s.t("Later", "পরে করব"),
                    Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp), prominent = allDone,
                ) { close() }
            }
        }
    }
}

private class Step(val title: String, val body: String, val done: Boolean, val action: String, val hint: String?, val go: () -> Unit)

@Composable
private fun StepRow(n: Int, st: Step, s: Strings) {
    val p = P
    val ring by animateColorAsState(if (st.done) p.ok else p.faint.copy(alpha = 0.25f), tween(300), label = "ring")
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
            AnimatedContent(st.done, transitionSpec = { scaleIn(Springs.bouncy(), 0.3f) + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "tick") { d ->
                if (d) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                else Text(s.num(n), color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(st.title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(st.body, color = p.sub, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            AnimatedVisibility(st.hint != null && !st.done, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(st.hint.orEmpty(), color = p.warn, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        AnimatedVisibility(!st.done, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Text(st.action, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp).pressable(onClick = st.go).clip(CircleShape).background(p.accent)
                    .padding(horizontal = 14.dp, vertical = 7.dp))
        }
        if (st.done) Box(Modifier.width(0.dp).height(0.dp))
    }
}

private fun brand() = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

private fun safeStart(ctx: Context, vararg intents: Intent) {
    for (i in intents) try { ctx.startActivity(i); return } catch (_: ActivityNotFoundException) {}
}

private fun appInfo(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))

fun openAppInfo(ctx: Context) = safeStart(ctx, appInfo(ctx))

/** Android 11+ can jump straight to BartaGuard's own switch; older versions get the full list. */
fun openListenerSettings(ctx: Context) {
    val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    if (Build.VERSION.SDK_INT >= 30) safeStart(ctx,
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(ctx, WatchService::class.java).flattenToString()), list)
    else safeStart(ctx, list)
}

private fun openAlertSettings(ctx: Context) =
    safeStart(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName), appInfo(ctx))
