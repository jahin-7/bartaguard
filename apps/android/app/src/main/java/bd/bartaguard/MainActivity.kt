package bd.bartaguard

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bd.bartaguard.ui.GlassBackdrop
import bd.bartaguard.ui.GlassTabBar
import bd.bartaguard.ui.GlassTheme
import bd.bartaguard.ui.P
import bd.bartaguard.ui.Springs
import bd.bartaguard.ui.Strings
import bd.bartaguard.ui.Tilt
import bd.bartaguard.ui.barStyle
import bd.bartaguard.ui.screens.CheckScreen
import bd.bartaguard.ui.screens.LanguageScreen
import bd.bartaguard.ui.screens.LearnScreen
import bd.bartaguard.ui.screens.LinksScreen
import bd.bartaguard.ui.screens.SettingsScreen
import bd.bartaguard.ui.screens.SetupSheet
import bd.bartaguard.ui.screens.screenTitle
import bd.bartaguard.ui.verdictColor
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            val dark = when (vm.appearance) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(bar, bar)
            }
            GlassTheme(dark) {
                val s = Strings(vm.lang != "en")
                val haze = remember { HazeState() }
                val scrolls = List(4) { rememberScrollState() }
                val scope = rememberCoroutineScope()
                val tint = vm.checked?.second?.verdict?.let { P.verdictColor(it) }?.takeIf { vm.tab == 0 }

                AnimatedContent(
                    vm.lang == null,
                    transitionSpec = { (fadeIn(tween(420)) + scaleIn(Springs.smooth(), initialScale = 1.06f)) togetherWith (fadeOut(tween(250)) + scaleOut(targetScale = 0.94f)) },
                    label = "onboarding",
                ) { first ->
                    if (first) {
                        GlassBackdrop { LanguageScreen(vm::setLanguage) }
                        return@AnimatedContent
                    }
                    Box(Modifier.fillMaxSize()) {
                        // Everything that scrolls is the haze source; the bars on top blur it.
                        GlassBackdrop(tint, Modifier.hazeSource(haze)) {
                            AnimatedContent(
                                vm.tab,
                                transitionSpec = {
                                    (fadeIn(tween(240, delayMillis = 40)) + scaleIn(Springs.smooth(), initialScale = 0.97f)) togetherWith
                                        (fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 1.015f))
                                },
                                label = "tab",
                            ) { tab ->
                                when (tab) {
                                    0 -> CheckScreen(vm, s, scrolls[0])
                                    1 -> LinksScreen(vm, s, scrolls[1])
                                    2 -> LearnScreen(s, scrolls[2])
                                    else -> SettingsScreen(vm, s, scrolls[3])
                                }
                            }
                        }

                        // Scroll edge: a soft blur under the status bar that fades in once content
                        // goes under it, and the small title that replaces the large one.
                        val px = with(LocalDensity.current) { 1.dp.toPx() }
                        val scroll = scrolls[vm.tab]
                        val p = P
                        Box(
                            Modifier.fillMaxWidth()
                                .hazeEffect(haze, barStyle(p)) {
                                    alpha = (scroll.value / (24 * px)).coerceIn(0f, 1f)
                                    mask = Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent)
                                }
                                .statusBarsPadding().height(52.dp),
                        ) {
                            Text(
                                screenTitle(vm.tab, s), color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                modifier = Modifier.align(Alignment.Center).padding(bottom = 10.dp).graphicsLayer {
                                    val t = ((scroll.value - 44 * px) / (22 * px)).coerceIn(0f, 1f)
                                    alpha = t; translationY = (1f - t) * 6 * px
                                },
                            )
                        }

                        GlassTabBar(
                            listOf(Icons.Outlined.VerifiedUser to s.tabCheck, Icons.Outlined.Link to s.tabLinks,
                                Icons.AutoMirrored.Outlined.MenuBook to s.tabLearn, Icons.Outlined.Settings to s.tabSettings),
                            vm.tab, haze,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp).fillMaxWidth(),
                        ) {
                            // Tapping the current tab scrolls back to the top, as on iPhone.
                            if (it == vm.tab) scope.launch { scrolls[it].animateScrollTo(0, Springs.smooth()) } else vm.tab = it
                        }

                        SetupSheet(vm, s)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAccess()
        Tilt.start(this)
    }

    override fun onPause() {
        super.onPause()
        Tilt.stop()
    }

    private fun handle(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> i?.getStringExtra(Intent.EXTRA_TEXT)
        }
        if (!text.isNullOrBlank()) vm.receive(text, i?.getBooleanExtra(EXTRA_FROM_WATCH, false) == true)
    }

    companion object {
        const val EXTRA_FROM_WATCH = "bd.bartaguard.FROM_WATCH"
    }
}
