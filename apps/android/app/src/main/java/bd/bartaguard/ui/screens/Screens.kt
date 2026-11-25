package bd.bartaguard.ui.screens

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import bd.bartaguard.ui.LargeTitle
import bd.bartaguard.ui.P
import bd.bartaguard.ui.SectionLabel
import bd.bartaguard.ui.Strings
import bd.bartaguard.ui.glass
import bd.bartaguard.ui.verdictColor
import java.text.DateFormat
import java.util.Date

private val screenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp)

@Composable
private fun Screen(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

// region First open

@Composable
fun LanguageScreen(choose: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).glass(P, CircleShape, strong = true), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.GppMaybe, null, tint = P.accent, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("BartaGuard", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = P.text)
        Text("বার্তা যাচাই · Check a message before you trust it", color = P.sub, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 32.dp))
        GlassButton("বাংলা", Modifier.fillMaxWidth(), prominent = true) { choose("bn") }
        Spacer(Modifier.height(12.dp))
        GlassButton("English", Modifier.fillMaxWidth()) { choose("en") }
        Spacer(Modifier.height(20.dp))
        Text("Works offline. Messages never leave your phone.\nঅফলাইনে কাজ করে। মেসেজ ফোনের বাইরে যায় না।",
            color = P.faint, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// endregion
// region Check

@Composable
fun CheckScreen(vm: AppViewModel, s: Strings) {
    val ctx = LocalContext.current
    val result = vm.checked
    Screen {
        item { LargeTitle(s.t("Check a message", "মেসেজ যাচাই"), s.t("On your phone. Nothing is uploaded.", "আপনার ফোনেই। কিছুই আপলোড হয় না।")) }
        if (result == null) {
            item {
                GlassCard(strong = true, padding = 16.dp) {
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
                            enabled = vm.ready && vm.input.isNotBlank()) { vm.check() }
                    }
                }
            }
            item { WatchTip(vm, s) }
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Share, null, tint = P.accent, modifier = Modifier.size(22.dp))
                        Text(s.t("From any app: long-press a message → Share → BartaGuard. Or select text → BartaGuard.",
                            "যেকোনো অ্যাপ থেকে: মেসেজ চেপে ধরুন → শেয়ার → BartaGuard। অথবা লেখা সিলেক্ট করে → BartaGuard।"),
                            color = P.sub, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        } else {
            item { ResultHero(result.second, s, vm.fromWatch) }
            item { HighlightedMessage(result.first, result.second, s) }
            if (result.second.flags.isNotEmpty()) {
                item { SectionLabel(s.t("What we noticed", "যা লক্ষ করা গেছে")) }
                items(result.second.flags.size) { FlagRow(result.second.flags[it], s) }
            }
            if (result.second.verdict == Verdict.SUSPICIOUS) item { WhatToDo(s) }
            item {
                Text(listOfNotNull(s.modelLine(result.second), s.t("Checked on this phone", "এই ফোনেই যাচাই হয়েছে")).joinToString(" · "),
                    color = P.faint, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
            item { GlassButton(s.t("Check another message", "আরেকটি মেসেজ যাচাই"), Modifier.fillMaxWidth(), icon = Icons.Outlined.Refresh) { vm.reset() } }
        }
    }
}

private fun verdictIcon(v: Verdict): ImageVector = when (v) {
    Verdict.SUSPICIOUS -> Icons.Outlined.WarningAmber
    Verdict.UNCERTAIN -> Icons.Outlined.HelpOutline
    Verdict.PROMOTIONAL -> Icons.Outlined.Campaign
    Verdict.NORMAL -> Icons.Outlined.CheckCircle
}

@Composable
private fun ResultHero(a: Analysis, s: Strings, fromWatch: Boolean) {
    val c = P.verdictColor(a.verdict)
    AnimatedVisibility(true, enter = fadeIn() + scaleIn(initialScale = 0.94f)) {
        GlassCard(strong = true, padding = 24.dp) {
            if (fromWatch) Chip(s.t("Auto-checked from a notification", "নোটিফিকেশন থেকে নিজে থেকেই যাচাই"), P.accent, Modifier.padding(bottom = 14.dp))
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(verdictIcon(a.verdict), null, tint = c, modifier = Modifier.size(36.dp))
            }
            Text(s.title(a.verdict), color = P.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp,
                modifier = Modifier.padding(top = 14.dp))
            Text(s.summary(a), color = P.sub, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun HighlightedMessage(text: String, a: Analysis, s: Strings) {
    val p = P
    // One severity per character, so overlapping matches read as one clean highlight.
    val level = IntArray(text.length)
    for (f in a.flags) if (f.severity > 0) for (r in f.spans) for (i in r) if (i < text.length) level[i] = maxOf(level[i], f.severity)
    val annotated = buildAnnotatedString {
        append(text)
        var i = 0
        while (i < text.length) {
            var j = i
            while (j < text.length && level[j] == level[i]) j++
            if (level[i] > 0) {
                val c = if (level[i] >= 2) p.danger else p.warn
                addStyle(SpanStyle(background = c.copy(alpha = if (p.dark) 0.32f else 0.2f), color = p.text, fontWeight = FontWeight.SemiBold), i, j)
            }
            i = j
        }
    }
    GlassCard {
        Text(s.t("The message", "মেসেজটি"), color = P.faint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(annotated, color = P.text, fontSize = 17.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun FlagRow(f: Flag, s: Strings) {
    val c = when { f.severity >= 3 -> P.danger; f.severity == 2 -> P.danger.copy(alpha = 0.85f); f.severity == 1 -> P.warn; else -> P.ok }
    GlassCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(c))
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
private fun WhatToDo(s: Strings) {
    GlassCard {
        Text(s.t("What to do", "এখন যা করবেন"), color = P.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        listOf(
            s.t("Don't reply or call the number in the message.", "মেসেজের নম্বরে উত্তর বা কল দেবেন না।"),
            s.t("Never share a PIN, OTP or password, even with \"customer care\".", "\"কাস্টমার কেয়ার\" হলেও পিন, OTP বা পাসওয়ার্ড দেবেন না।"),
            s.t("Check your balance or account in the official app.", "অফিসিয়াল অ্যাপে ব্যালেন্স বা অ্যাকাউন্ট দেখুন।"),
            s.t("If you already sent money or a code, call your provider's official helpline now.", "যদি টাকা বা কোড পাঠিয়ে থাকেন, এখনই প্রতিষ্ঠানের অফিসিয়াল হেল্পলাইনে ফোন করুন।"),
        ).forEachIndexed { i, line ->
            Row(Modifier.padding(top = 10.dp)) {
                Text(s.n(i + 1), color = P.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.size(22.dp))
                Text(line, color = P.sub, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun WatchTip(vm: AppViewModel, s: Strings) {
    val on = vm.watch && vm.access
    GlassCard(modifier = Modifier.clickable { vm.tab = 3 }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsActive, null, tint = if (on) P.ok else P.accent, modifier = Modifier.size(22.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(if (on) s.t("Auto-check is on", "অটো-যাচাই চালু") else s.t("Check new messages automatically", "নতুন মেসেজ নিজে থেকেই যাচাই করুন"),
                    color = P.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(if (on) s.t("You'll get a warning if a message looks like a scam.", "কোনো মেসেজ প্রতারণা মনে হলে সতর্কবার্তা পাবেন।")
                     else s.t("Turn on in Settings. Runs on your phone only.", "সেটিংসে চালু করুন। শুধু আপনার ফোনেই চলে।"), color = P.sub, fontSize = 13.sp)
            }
        }
    }
}

// endregion
// region Links

@Composable
fun LinksScreen(vm: AppViewModel, s: Strings) {
    Screen {
        item { LargeTitle(s.t("Check a link", "লিংক যাচাই"), s.t("We read the address. We never open it.", "আমরা শুধু ঠিকানাটি পড়ি, কখনো খুলি না।")) }
        item {
            GlassCard(strong = true, padding = 16.dp) {
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    if (vm.linkInput.isEmpty()) Text("bit.ly/… · bkash-verify.xyz", color = P.faint, fontSize = 17.sp)
                    BasicTextField(vm.linkInput, { vm.linkInput = it }, Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.inspect() }),
                        textStyle = TextStyle(color = P.text, fontSize = 17.sp), cursorBrush = SolidColor(P.accent))
                }
                GlassButton(s.t("Inspect", "পরীক্ষা করুন"), Modifier.fillMaxWidth().padding(top = 12.dp), icon = Icons.Outlined.Search,
                    prominent = true, enabled = vm.ready && vm.linkInput.isNotBlank()) { vm.inspect() }
            }
        }
        vm.link?.let { info ->
            item {
                val bad = info.flags.any { it != "not_https" }
                val c = if (bad) P.danger else if (info.flags.isEmpty()) P.ok else P.warn
                GlassCard(modifier = Modifier.animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (bad) Icons.Outlined.WarningAmber else Icons.Outlined.Lock, null, tint = c, modifier = Modifier.size(26.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(if (bad) s.t("Don't open this link", "এই লিংকে যাবেন না") else if (info.flags.isEmpty()) s.t("No warning signs in the address", "ঠিকানায় সতর্কসংকেত নেই") else s.t("Be careful", "সাবধান"),
                                color = P.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(info.host.ifEmpty { info.url }, color = P.sub, fontSize = 14.sp)
                        }
                    }
                    info.flags.forEach { f ->
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
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
}

// endregion
// region Learn

@Composable
fun LearnScreen(s: Strings) {
    Screen {
        item { LargeTitle(s.t("Common scams", "প্রচলিত প্রতারণা"), s.t("Know the tricks before they reach you.", "কৌশলগুলো আগে থেকেই জেনে রাখুন।")) }
        items(GUIDES.size) { i ->
            val g = GUIDES[i]
            var open by remember { mutableStateOf(i == 0) }
            GlassCard(Modifier.animateContentSize().clickable { open = !open }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.t(g.en, g.bn), color = P.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = P.faint)
                }
                if (open) {
                    Text(s.t(g.signsEn, g.signsBn), color = P.sub, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.ok.copy(alpha = 0.12f)).padding(12.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = P.ok, modifier = Modifier.size(20.dp))
                        Text(s.t(g.doEn, g.doBn), color = P.text, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
        item {
            GlassCard {
                Text(s.t("In danger or being threatened? Call 999.", "বিপদে বা হুমকির মুখে? ৯৯৯-এ কল করুন।"), color = P.text, fontWeight = FontWeight.SemiBold)
                Text(s.t("For money sent to a scammer, call your bank's or MFS provider's official helpline right away. The number is in their official app.",
                    "প্রতারকের কাছে টাকা গেলে এখনই আপনার ব্যাংক বা MFS-এর অফিসিয়াল হেল্পলাইনে ফোন করুন। নম্বরটি তাদের অফিসিয়াল অ্যাপে আছে।"),
                    color = P.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// endregion
// region Settings

@Composable
fun SettingsScreen(vm: AppViewModel, s: Strings) {
    val ctx = LocalContext.current
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Screen {
        item { LargeTitle(s.t("Settings", "সেটিংস")) }
        item { SectionLabel(s.t("Language", "ভাষা")) }
        item {
            Row(Modifier.fillMaxWidth().glass(P, CircleShape, elevation = 8.dp).padding(4.dp)) {
                listOf("bn" to "বাংলা", "en" to "English").forEach { (k, label) ->
                    val on = (vm.lang ?: "bn") == k
                    Text(label, color = if (on) Color.White else P.text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(CircleShape).background(if (on) P.accent else Color.Transparent)
                            .clickable { vm.setLanguage(k) }.padding(vertical = 10.dp))
                }
            }
        }
        item { SectionLabel(s.t("Automatic checking", "স্বয়ংক্রিয় যাচাই")) }
        item {
            GlassCard {
                ToggleRow(s.t("Auto-check new messages", "নতুন মেসেজ অটো-যাচাই"),
                    s.t("Reads new SMS, WhatsApp, Messenger, Telegram and imo notifications on this phone and warns you about likely scams. Nothing is sent anywhere.",
                        "এই ফোনে নতুন SMS, হোয়াটসঅ্যাপ, মেসেঞ্জার, টেলিগ্রাম ও imo নোটিফিকেশন পড়ে সম্ভাব্য প্রতারণা নিয়ে সতর্ক করে। কিছুই কোথাও পাঠানো হয় না।"),
                    vm.watch && vm.access) { on ->
                    if (on) {
                        vm.setWatching(true)
                        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        WatchService.ensureChannel(ctx, s.bn)
                        if (!vm.access) ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } else vm.setWatching(false)
                }
                if (vm.watch && !vm.access) {
                    Text(s.t("Allow BartaGuard under Notification access, then come back.", "নোটিফিকেশন অ্যাক্সেসে BartaGuard-কে অনুমতি দিন, তারপর ফিরে আসুন।"),
                        color = P.warn, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        item { SectionLabel(s.t("History", "ইতিহাস")) }
        item {
            GlassCard {
                ToggleRow(s.t("Keep a list of my checks", "যাচাইয়ের তালিকা রাখুন"),
                    s.t("Off by default. Stored only on this phone, first 90 characters only.", "ডিফল্ট বন্ধ। শুধু এই ফোনে, প্রথম ৯০ অক্ষর পর্যন্ত।"), vm.keepHistory, vm::setHistory)
                if (vm.keepHistory && vm.history.isNotEmpty()) {
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
                        modifier = Modifier.padding(top = 14.dp).clickable { vm.clearHistory() })
                }
            }
        }
        item { SectionLabel(s.t("Privacy and accuracy", "গোপনীয়তা ও নির্ভুলতা")) }
        item {
            GlassCard {
                Text(s.t("Every check runs on this phone. BartaGuard has no account, no ads and no internet permission.",
                    "প্রতিটি যাচাই এই ফোনেই হয়। BartaGuard-এ কোনো অ্যাকাউন্ট, বিজ্ঞাপন বা ইন্টারনেট অনুমতি নেই।"), color = P.text, fontSize = 15.sp, lineHeight = 22.sp)
                Text(s.t("On held-out test messages it flagged 90% of scams as suspicious and called 2% safe. For SMS scams that miss rate is higher (15%), and it was trained on Bangla only. Use it as a second opinion, not a guarantee.",
                    "পরীক্ষার মেসেজে এটি ৯০% প্রতারণাকে সন্দেহজনক বলেছে এবং ২%-কে নিরাপদ বলেছে। SMS প্রতারণায় ভুলের হার বেশি (১৫%), আর এটি শুধু বাংলায় প্রশিক্ষিত। এটিকে দ্বিতীয় মতামত হিসেবে নিন, নিশ্চয়তা হিসেবে নয়।"),
                    color = P.sub, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
            }
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
        Switch(on, set, colors = SwitchDefaults.colors(checkedTrackColor = P.ok, checkedThumbColor = Color.White,
            uncheckedTrackColor = P.faint.copy(alpha = 0.3f), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent))
    }
}

// endregion

