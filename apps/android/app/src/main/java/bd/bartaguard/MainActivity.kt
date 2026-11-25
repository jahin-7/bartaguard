package bd.bartaguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import bd.bartaguard.ui.GlassBackdrop
import bd.bartaguard.ui.GlassTabBar
import bd.bartaguard.ui.GlassTheme
import bd.bartaguard.ui.P
import bd.bartaguard.ui.Strings
import bd.bartaguard.ui.verdictColor
import bd.bartaguard.ui.screens.CheckScreen
import bd.bartaguard.ui.screens.LanguageScreen
import bd.bartaguard.ui.screens.LearnScreen
import bd.bartaguard.ui.screens.LinksScreen
import bd.bartaguard.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            GlassTheme {
                val s = Strings(vm.lang != "en")
                val tint = vm.checked?.second?.verdict?.let { P.verdictColor(it) }?.takeIf { vm.tab == 0 }
                GlassBackdrop(tint) {
                    if (vm.lang == null) {
                        LanguageScreen(vm::setLanguage)
                        return@GlassBackdrop
                    }
                    AnimatedContent(vm.tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { tab ->
                        when (tab) {
                            0 -> CheckScreen(vm, s)
                            1 -> LinksScreen(vm, s)
                            2 -> LearnScreen(s)
                            else -> SettingsScreen(vm, s)
                        }
                    }
                    GlassTabBar(
                        listOf(Icons.Outlined.Shield to s.tabCheck, Icons.Outlined.Link to s.tabLinks,
                            Icons.Outlined.MenuBook to s.tabLearn, Icons.Outlined.Settings to s.tabSettings),
                        vm.tab,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth(),
                    ) { vm.tab = it }
                    // Fade under the status bar so scrolled content doesn't collide with the clock.
                    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(P.bg, P.bg.copy(alpha = 0f)))).statusBarsPadding().height(12.dp))
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
