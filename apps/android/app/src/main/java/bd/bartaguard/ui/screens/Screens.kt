package bd.bartaguard.ui.screens

import android.Manifest
import bd.bartaguard.ui.outerShadow
import bd.bartaguard.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bd.bartaguard.AppViewModel
import bd.bartaguard.WatchService
import bd.bartaguard.engine.Analysis
import bd.bartaguard.engine.Flag
import bd.bartaguard.engine.Verdict
import bd.bartaguard.ui.Chip
import bd.bartaguard.ui.GUIDES
import bd.bartaguard.ui.GlassButton
import bd.bartaguard.ui.GlassCard
import bd.bartaguard.ui.Haptic
import bd.bartaguard.ui.IosSwitch
import bd.bartaguard.ui.LargeTitle
import bd.bartaguard.ui.P
import bd.bartaguard.ui.SectionLabel
import bd.bartaguard.ui.Segmented
import bd.bartaguard.ui.Springs
import bd.bartaguard.ui.Strings
import bd.bartaguard.ui.appear
import bd.bartaguard.ui.glass
import bd.bartaguard.ui.haptic
import bd.bartaguard.ui.pressable
import bd.bartaguard.ui.rememberRubberBand
import bd.bartaguard.ui.titleStretch
import bd.bartaguard.ui.verdictColor
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

fun screenTitle(tab: Int, s: Strings) = when (tab) {
    0 -> s.t("Check a message", "মেসেজ যাচাই")
    1 -> s.t("Check a link", "লিংক যাচাই")
    2 -> s.t("Common scams", "প্রচলিত প্রতারণা")
    else -> s.t("Settings", "সেটিংস")
}

/** Large-title page over the colour field. Rubber-bands at both ends; the title stretches when pulled. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Screen(scroll: ScrollState, title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val rb = rememberRubberBand()
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Column(
            Modifier.fillMaxSize().nestedScroll(rb).verticalScroll(scroll)
                .graphicsLayer { translationY = rb.offset }
                .statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LargeTitle(title, sub, Modifier.titleStretch { rb.offset })
            content()
        }
    }
}

// region First open

@Composable
fun LanguageScreen(choose: (String) -> Unit) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 200f)) }
    val float by rememberInfiniteTransition(label = "float")
        .animateFloat(-1f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "y")
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
                translationY = float * 4.dp.toPx()
            }.size(104.dp).outerShadow(RoundedCornerShape(26.dp), 18.dp, Color.Black.copy(alpha = 0.25f)).clip(RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // The app icon itself: the adaptive layers, foreground enlarged past its safe-zone inset.
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.55f; scaleY = 1.55f })
        }
        Spacer(Modifier.height(24.dp))
        Text("BartaGuard", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = P.text, modifier = Modifier.appear(1))
        Text("বার্তা যাচাই · Check a message before you trust it", color = P.sub, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.appear(2).padding(top = 6.dp, bottom = 32.dp))
        GlassButton("বাংলা", Modifier.fillMaxWidth().appear(3), prominent = true) { choose("bn") }
        Spacer(Modifier.height(12.dp))
        GlassButton("English", Modifier.fillMaxWidth().appear(4)) { choose("en") }
        Spacer(Modifier.height(20.dp))
        Text("Works offline. Messages never leave your phone.\nঅফলাইনে কাজ করে। মেসেজ ফোনের বাইরে যায় না।",
            color = P.faint, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.appear(5))
    }
}

// endregion
// region Check

@Composable
fun CheckScreen(vm: AppViewModel, s: Strings, scroll: ScrollState) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    Screen(scroll, screenTitle(0, s), s.t("On your phone. Nothing is uploaded.", "আপনার ফোনেই। কিছুই আপলোড হয় না।")) {
        AnimatedContent(
            vm.checked,
            contentKey = { it?.first },
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(Springs.smooth(), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.96f)) using SizeTransform(clip = false)
            },
            label = "check",
        ) { result ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (result == null) {
                    GlassCard(Modifier.appear(0), strong = true, padding = 16.dp) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 140.dp)) {
                            if (vm.input.isEmpty()) Text(s.t("Paste or type the SMS, WhatsApp or Messenger message here…",
                                "এখানে SMS, হোয়াটসঅ্যাপ বা মেসেঞ্জারের মেসেজটি পেস্ট করুন বা লিখুন…"), color = P.faint, fontSize = 17.sp)
                            BasicTextField(vm.input, { vm.input = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
                                textStyle = TextStyle(color = P.text, fontSize = 17.sp, lineHeight = 25.sp), cursorBrush = SolidColor(P.accent))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(s.t("Paste", "পেস্ট"), icon = Icons.Outlined.ContentPaste) {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.let { vm.input = it.toString() }
                            }
                            GlassButton(s.t("Check", "যাচাই করুন"), Modifier.weight(1f), icon = Icons.Outlined.Search, prominent = true,
                                enabled = vm.ready && vm.input.isNotBlank()) { focus.clearFocus(); vm.check() }
                        }
                    }
                    WatchTip(vm, s, Modifier.appear(1))
                    Tip(Icons.Outlined.Share, s.t("WhatsApp, Messenger, imo…", "হোয়াটসঅ্যাপ, মেসেঞ্জার, imo…"),
                        s.t("Long-press a message → Share → BartaGuard. Or select text → BartaGuard.",
                            "মেসেজ চেপে ধরুন → শেয়ার → BartaGuard। অথবা লেখা সিলেক্ট করে → BartaGuard।"), Modifier.appear(2))
                } else {
                    val (text, a) = result
                    ResultHero(a, s, vm.fromWatch, Modifier.appear(0, a))
                    HighlightedMessage(text, a, s, Modifier.appear(1, a))
                    var i = 2
                    if (a.flags.isNotEmpty()) {
                        Box(Modifier.appear(i++, a)) { SectionLabel(s.t("What we noticed", "যা লক্ষ করা গেছে")) }
                        a.flags.forEach { FlagRow(it, s, Modifier.appear(i++, a)) }
                    }
                    if (a.verdict == Verdict.SUSPICIOUS) WhatToDo(s, Modifier.appear(i++, a))
                    Text(listOfNotNull(s.modelLine(a), s.t("Checked on this phone", "এই ফোনেই যাচাই হয়েছে")).joinToString(" · "),
                        color = P.faint, fontSize = 13.sp, modifier = Modifier.appear(i++, a).padding(horizontal = 8.dp))
                    GlassButton(s.t("Check another message", "আরেকটি মেসেজ যাচাই"), Modifier.fillMaxWidth().appear(i, a),
                        icon = Icons.Outlined.Refresh) { vm.reset() }
                }
            }
        }
    }
}

private fun verdictIcon(v: Verdict): ImageVector = when (v) {
    Verdict.SUSPICIOUS -> Icons.Filled.Warning
    Verdict.UNCERTAIN -> Icons.AutoMirrored.Filled.Help
    Verdict.PROMOTIONAL -> Icons.Filled.Campaign
    Verdict.NORMAL -> Icons.Filled.VerifiedUser
}

/**
 * The verdict. The symbol pops in with a bounce and a haptic; a scam also gets a wiggle and a
 * slow warning pulse around it (SF Symbols' bounce and wiggle effects).
 */
@Composable
private fun ResultHero(a: Analysis, s: Strings, fromWatch: Boolean, modifier: Modifier = Modifier) {
    val c = P.verdictColor(a.verdict)
    val view = LocalView.current
    val pop = remember(a) { Animatable(0f) }
    val wiggle = remember(a) { Animatable(0f) }
    val bad = a.verdict == Verdict.SUSPICIOUS
    LaunchedEffect(a) {
        delay(140)
        view.haptic(if (bad) Haptic.Warning else Haptic.Success)
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 360f))
        if (bad) repeat(2) {
            wiggle.animateTo(1f, tween(60)); wiggle.animateTo(-1f, tween(120)); wiggle.animateTo(0f, tween(60))
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ring")
    GlassCard(modifier, strong = true, padding = 24.dp, tint = c) {
        if (fromWatch) Chip(s.t("Auto-checked from a notification", "নোটিফিকেশন থেকে নিজে থেকেই যাচাই"), P.accent, Modifier.padding(bottom = 14.dp))
        Box(
            Modifier.size(64.dp).drawBehind {
                if (bad && pop.value > 0.9f) {
                    val r = size.minDimension / 2 * (1f + pulse * 0.6f)
                    drawCircle(c.copy(alpha = 0.35f * (1f - pulse)), r)
                }
            }.graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = wiggle.value * 12f }
                .clip(CircleShape).background(c.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(verdictIcon(a.verdict), null, tint = c, modifier = Modifier.size(34.dp))
        }
        Text(s.title(a.verdict), color = P.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp,
            modifier = Modifier.padding(top = 14.dp))
        Text(s.summary(a), color = P.sub, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun HighlightedMessage(text: String, a: Analysis, s: Strings, modifier: Modifier = Modifier) {
    val p = P
    // One severity per character, so overlapping matches read as one clean highlight.
    val level = IntArray(text.length)
    for (f in a.flags) if (f.severity > 0) for (r in f.spans) for (i in r) if (i < text.length) level[i] = maxOf(level[i], f.severity)
    // The highlights fade in a moment after the card lands, drawing the eye to them.
    val shown = remember(a) { Animatable(0f) }
    LaunchedEffect(a) { delay(450); shown.animateTo(1f, tween(500)) }
    val glow = shown.value
    val annotated = buildAnnotatedString {
        append(text)
        var i = 0
        while (i < text.length) {
            var j = i
            while (j < text.length && level[j] == level[i]) j++
            if (level[i] > 0) {
                val c = if (level[i] >= 2) p.danger else p.warn
                addStyle(SpanStyle(background = c.copy(alpha = glow * if (p.dark) 0.32f else 0.2f), color = p.text, fontWeight = FontWeight.SemiBold), i, j)
            }
            i = j
        }
    }
    GlassCard(modifier) {
        Text(s.t("The message", "মেসেজটি"), color = P.faint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(annotated, color = P.text, fontSize = 17.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun FlagRow(f: Flag, s: Strings, modifier: Modifier = Modifier) {
    val c = when { f.severity >= 2 -> P.danger; f.severity == 1 -> P.warn; else -> P.ok }
    GlassCard(modifier, padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(c))
            Column(Modifier.padding(start = 12.dp)) {
                if (f.link != null) {
                    Text(s.t("Suspicious link: don't open it. Type the official address yourself.", "সন্দেহজনক লিংক: খুলবেন না। অফিসিয়াল ঠিকানা নিজে টাইপ করুন।"),
                        color = P.text, fontSize = 15.sp, lineHeight = 22.sp)
                    Text(f.link.host, color = P.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    f.link.flags.forEach { Text("• " + s.linkFlag(it), color = P.sub, fontSize = 14.sp, lineHeight = 20.sp) }
                } else {
                    Text(if (s.bn) f.bn else f.en, color = P.text, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun WhatToDo(s: Strings, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Text(s.t("What to do", "এখন যা করবেন"), color = P.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        listOf(
            s.t("Don't reply or call the number in the message.", "মেসেজের নম্বরে উত্তর বা কল দেবেন না।"),
            s.t("Never share a PIN, OTP or password, even with \"customer care\".", "\"কাস্টমার কেয়ার\" হলেও পিন, OTP বা পাসওয়ার্ড দেবেন না।"),
            s.t("Check your balance or account in the official app.", "অফিসিয়াল অ্যাপে ব্যালেন্স বা অ্যাকাউন্ট দেখুন।"),
            s.t("If you already sent money or a code, call your provider's official helpline now.", "যদি টাকা বা কোড পাঠিয়ে থাকেন, এখনই প্রতিষ্ঠানের অফিসিয়াল হেল্পলাইনে ফোন করুন।"),
        ).forEachIndexed { i, line ->
            Row(Modifier.padding(top = 10.dp)) {
                Text(s.n(i + 1), color = P.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(22.dp))
                Text(line, color = P.sub, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, tint: Color = P.accent, onClick: (() -> Unit)? = null) {
    GlassCard(modifier, padding = 18.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(24.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, color = P.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(body, color = P.sub, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun WatchTip(vm: AppViewModel, s: Strings, modifier: Modifier = Modifier) {
    val on = vm.watching
    Tip(
        Icons.Outlined.NotificationsActive,
        if (on) s.t("Auto-check is on", "অটো-যাচাই চালু") else s.t("Check new messages automatically", "নতুন মেসেজ নিজে থেকেই যাচাই করুন"),
        if (on) s.t("You'll get a warning if a message looks like a scam.", "কোনো মেসেজ প্রতারণা মনে হলে সতর্কবার্তা পাবেন।")
        else s.t("Tap to set up. Runs on your phone only.", "সেটআপ করতে চাপুন। শুধু আপনার ফোনেই চলে।"),
        modifier, tint = if (on) P.ok else P.accent,
    ) { if (on) vm.tab = 3 else { vm.setWatching(true); vm.setupOpen = true } }
}

// endregion
// region Links

@Composable
fun LinksScreen(vm: AppViewModel, s: Strings, scroll: ScrollState) {
    val focus = LocalFocusManager.current
    Screen(scroll, screenTitle(1, s), s.t("We read the address. We never open it.", "আমরা শুধু ঠিকানাটি পড়ি, কখনো খুলি না।")) {
        GlassCard(Modifier.appear(0), strong = true, padding = 16.dp) {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                if (vm.linkInput.isEmpty()) Text("bit.ly/… · bkash-verify.xyz", color = P.faint, fontSize = 17.sp)
                BasicTextField(vm.linkInput, { vm.linkInput = it }, Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.inspect() }),
                    textStyle = TextStyle(color = P.text, fontSize = 17.sp), cursorBrush = SolidColor(P.accent))
            }
            GlassButton(s.t("Inspect", "পরীক্ষা করুন"), Modifier.fillMaxWidth().padding(top = 12.dp), icon = Icons.Outlined.Search,
                prominent = true, enabled = vm.ready && vm.linkInput.isNotBlank()) { focus.clearFocus(); vm.inspect() }
        }
        AnimatedContent(
            vm.link,
            transitionSpec = { (fadeIn(tween(200)) + scaleIn(Springs.bouncy(), initialScale = 0.9f)) togetherWith fadeOut(tween(120)) },
            label = "link",
        ) { info ->
            if (info == null) return@AnimatedContent
            val view = LocalView.current
            val bad = info.flags.any { it != "not_https" }
            val c = if (bad) P.danger else if (info.flags.isEmpty()) P.ok else P.warn
            LaunchedEffect(info) { view.haptic(if (bad) Haptic.Warning else Haptic.Success) }
            GlassCard(tint = c) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (bad) Icons.Filled.Warning else Icons.Filled.Lock, null, tint = c, modifier = Modifier.size(28.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(if (bad) s.t("Don't open this link", "এই লিংকে যাবেন না") else if (info.flags.isEmpty()) s.t("No warning signs in the address", "ঠিকানায় সতর্কসংকেত নেই") else s.t("Be careful", "সাবধান"),
                            color = P.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(info.host.ifEmpty { info.url }, color = P.sub, fontSize = 14.sp)
                    }
                }
                info.flags.forEachIndexed { i, f ->
                    Row(Modifier.padding(top = 12.dp).appear(i + 1, info, 16f), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (f == "not_https") P.warn else P.danger))
                        Text(s.linkFlag(f), color = P.text, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                Text(s.t("A clean address isn't proof a site is safe. When money is involved, type the official address yourself.",
                    "ঠিকানা ঠিক থাকলেই সাইট নিরাপদ নয়। টাকার বিষয় হলে অফিসিয়াল ঠিকানা নিজে টাইপ করুন।"),
                    color = P.faint, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }
}

// endregion
// region Learn

@Composable
fun LearnScreen(s: Strings, scroll: ScrollState) {
    Screen(scroll, screenTitle(2, s), s.t("Know the tricks before they reach you.", "কৌশলগুলো আগে থেকেই জেনে রাখুন।")) {
        GUIDES.forEachIndexed { i, g ->
            var open by rememberSaveable { mutableStateOf(i == 0) }
            val turn by animateFloatAsState(if (open) 180f else 0f, Springs.bouncy(), label = "chev")
            GlassCard(Modifier.appear(i), onClick = { open = !open }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.t(g.en, g.bn), color = P.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.KeyboardArrowDown, null, tint = P.faint, modifier = Modifier.graphicsLayer { rotationZ = turn })
                }
                AnimatedVisibility(
                    open,
                    enter = expandVertically(Springs.smooth()) + fadeIn(tween(250, delayMillis = 60)),
                    exit = shrinkVertically(Springs.smooth()) + fadeOut(tween(120)),
                ) {
                    Column {
                        Text(s.t(g.signsEn, g.signsBn), color = P.sub, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.ok.copy(alpha = 0.12f)).padding(12.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = P.ok, modifier = Modifier.size(20.dp))
                            Text(s.t(g.doEn, g.doBn), color = P.text, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        }
        GlassCard(Modifier.appear(GUIDES.size)) {
            Text(s.t("In danger or being threatened? Call 999.", "বিপদে বা হুমকির মুখে? ৯৯৯-এ কল করুন।"), color = P.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(s.t("For money sent to a scammer, call your bank's or MFS provider's official helpline right away. The number is in their official app.",
                "প্রতারকের কাছে টাকা গেলে এখনই আপনার ব্যাংক বা MFS-এর অফিসিয়াল হেল্পলাইনে ফোন করুন। নম্বরটি তাদের অফিসিয়াল অ্যাপে আছে।"),
                color = P.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// endregion
// region Settings

@Composable
fun SettingsScreen(vm: AppViewModel, s: Strings, scroll: ScrollState) {
    val ctx = LocalContext.current
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Screen(scroll, screenTitle(3, s)) {
        SectionLabel(s.t("Language", "ভাষা"))
        Segmented(listOf("bn" to "বাংলা", "en" to "English"), vm.lang ?: "bn", Modifier.appear(0)) { vm.setLanguage(it) }

        SectionLabel(s.t("Appearance", "থিম"))
        Segmented(listOf("system" to s.t("Automatic", "অটো"), "light" to s.t("Light", "লাইট"), "dark" to s.t("Dark", "ডার্ক")),
            vm.appearance, Modifier.appear(1)) { vm.chooseAppearance(it) }

        SectionLabel(s.t("Automatic checking", "স্বয়ংক্রিয় যাচাই"))
        GlassCard(Modifier.appear(2)) {
            ToggleRow(s.t("Auto-check new messages", "নতুন মেসেজ অটো-যাচাই"),
                s.t("Reads new SMS, WhatsApp, Messenger, Telegram and imo notifications on this phone and warns you about likely scams. Nothing is sent anywhere.",
                    "এই ফোনে নতুন SMS, হোয়াটসঅ্যাপ, মেসেঞ্জার, টেলিগ্রাম ও imo নোটিফিকেশন পড়ে সম্ভাব্য প্রতারণা নিয়ে সতর্ক করে। কিছুই কোথাও পাঠানো হয় না।"),
                vm.watching) { on ->
                vm.setWatching(on)
                if (on && !vm.watching) vm.setupOpen = true
            }
            AnimatedVisibility(vm.watch && !vm.watching, enter = expandVertically(Springs.smooth()) + fadeIn(), exit = shrinkVertically(Springs.smooth()) + fadeOut()) {
                Text(s.t("Finish setup to get warnings →", "সতর্কবার্তা পেতে সেটআপ শেষ করুন →"), color = P.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp).pressable { vm.setupOpen = true })
            }
        }

        SectionLabel(s.t("History", "ইতিহাস"))
        GlassCard(Modifier.appear(3)) {
            ToggleRow(s.t("Keep a list of my checks", "যাচাইয়ের তালিকা রাখুন"),
                s.t("Off by default. Stored only on this phone, first 90 characters only.", "ডিফল্ট বন্ধ। শুধু এই ফোনে, প্রথম ৯০ অক্ষর পর্যন্ত।"), vm.keepHistory, vm::setHistory)
            AnimatedVisibility(vm.keepHistory && vm.history.isNotEmpty(), enter = expandVertically(Springs.smooth()) + fadeIn(), exit = shrinkVertically(Springs.smooth()) + fadeOut()) {
                Column {
                    vm.history.take(20).forEach { h ->
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(P.verdictColor(h.verdict)))
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(h.preview, color = P.text, fontSize = 14.sp, maxLines = 1)
                                Text("${s.title(h.verdict)} · ${h.source} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.time))}",
                                    color = P.faint, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                    Text(s.t("Clear history", "ইতিহাস মুছুন"), color = P.danger, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 14.dp).pressable(haptic = Haptic.Warning) { vm.clearHistory() })
                }
            }
        }

        SectionLabel(s.t("Privacy and accuracy", "গোপনীয়তা ও নির্ভুলতা"))
        GlassCard(Modifier.appear(4)) {
            Text(s.t("Every check runs on this phone. BartaGuard has no account, no ads and no internet permission.",
                "প্রতিটি যাচাই এই ফোনেই হয়। BartaGuard-এ কোনো অ্যাকাউন্ট, বিজ্ঞাপন বা ইন্টারনেট অনুমতি নেই।"), color = P.text, fontSize = 15.sp, lineHeight = 22.sp)
            Text(s.t("On held-out test messages it flagged 90% of scams as suspicious and called 2% safe. For SMS scams that miss rate is higher (15%), and it was trained on Bangla only. Use it as a second opinion, not a guarantee.",
                "পরীক্ষার মেসেজে এটি ৯০% প্রতারণাকে সন্দেহজনক বলেছে এবং ২%-কে নিরাপদ বলেছে। SMS প্রতারণায় ভুলের হার বেশি (১৫%), আর এটি শুধু বাংলায় প্রশিক্ষিত। এটিকে দ্বিতীয় মতামত হিসেবে নিন, নিশ্চয়তা হিসেবে নয়।"),
                color = P.sub, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = P.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = P.sub, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
        }
        IosSwitch(on, set)
    }
}

// endregion
