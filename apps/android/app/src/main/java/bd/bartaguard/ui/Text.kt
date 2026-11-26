package bd.bartaguard.ui

import bd.bartaguard.engine.Analysis
import bd.bartaguard.engine.Verdict
import kotlin.math.roundToInt

/** In-app language switch (not the system locale). Bangla first; the app is for Bangla speakers. */
class Strings(val bn: Boolean) {
    fun t(en: String, bnText: String) = if (bn) bnText else en
    /** Bangla digits in Bangla, so "২টির মধ্যে ১টি" doesn't mix scripts. */
    fun num(n: Int) = if (bn) n.toString().map { if (it.isDigit()) '০' + (it - '0') else it }.joinToString("") else n.toString()
    fun n(x: Int) = if (bn) x.toString().map { if (it in '0'..'9') "০১২৩৪৫৬৭৮৯"[it - '0'] else it }.joinToString("") else x.toString()
    fun pct(x: Double) = n((x * 100).roundToInt()) + "%"

    fun title(v: Verdict) = when (v) {
        Verdict.SUSPICIOUS -> t("Looks like a scam", "প্রতারণা মনে হচ্ছে")
        Verdict.UNCERTAIN -> t("Can't tell for sure", "নিশ্চিত বলা যাচ্ছে না")
        Verdict.PROMOTIONAL -> t("Looks like an ad", "বিজ্ঞাপন মনে হচ্ছে")
        Verdict.NORMAL -> t("No warning signs", "সতর্ক হওয়ার মতো কিছু পাওয়া যায়নি")
    }

    fun summary(a: Analysis) = when (a.verdict) {
        Verdict.SUSPICIOUS -> t("Don't reply, call back, open links, or send money or any code.",
            "উত্তর দেবেন না, ফোন করবেন না, লিংকে যাবেন না, টাকা বা কোনো কোড পাঠাবেন না।")
        Verdict.UNCERTAIN -> if (a.outOfDomain)
            t("BartaGuard's model was trained on Bangla messages. This one is mostly English or Banglish, so only the warning-sign checks ran. Be careful.",
                "BartaGuard-এর মডেল বাংলা মেসেজে প্রশিক্ষিত। এই মেসেজটি মূলত ইংরেজি বা বাংলিশ, তাই শুধু সতর্কসংকেত যাচাই হয়েছে। সাবধান থাকুন।")
        else t("Some signs point both ways. Verify through the official app or number before you act.",
            "কিছু লক্ষণ দুই দিকেই। কিছু করার আগে অফিসিয়াল অ্যাপ বা নম্বরে যাচাই করুন।")
        Verdict.PROMOTIONAL -> t("Seems to be a regular offer or ad. Buy only through official channels.",
            "সাধারণ অফার বা বিজ্ঞাপন মনে হচ্ছে। শুধু অফিসিয়াল মাধ্যমে কিনুন।")
        Verdict.NORMAL -> t("Nothing suspicious found. That isn't a guarantee: never share a PIN or OTP with anyone.",
            "সন্দেহজনক কিছু পাওয়া যায়নি। তবে এটি নিশ্চয়তা নয়: কাউকে পিন বা OTP দেবেন না।")
    }

    fun modelLine(a: Analysis): String? {
        val l = a.modelLabel ?: return null
        val c = a.modelConfidence ?: return null
        val name = when (l) { "SPAM" -> t("scam/spam", "প্রতারণা/স্প্যাম"); "PROMO" -> t("promotion", "প্রচারণা"); else -> t("normal", "সাধারণ") }
        return t("Text model: ${pct(c)} $name", "টেক্সট মডেল: ${pct(c)} $name")
    }

    fun linkFlag(f: String) = when {
        f == "shortener" -> t("Short link that hides the real address", "শর্ট লিংক, আসল ঠিকানা লুকানো")
        f == "punycode" -> t("Uses look-alike letters (punycode)", "দেখতে একই রকম অক্ষর ব্যবহার করেছে (punycode)")
        f == "ip_address" -> t("A bare number address instead of a name", "নামের বদলে শুধু সংখ্যার ঠিকানা")
        f == "risky_tld" -> t("Domain ending often used by scam sites", "প্রতারণার সাইটে প্রায়ই ব্যবহৃত ডোমেইন")
        f == "not_https" -> t("Not a secure (https) link", "নিরাপদ (https) লিংক নয়")
        f.startsWith("brand_lookalike:") -> t("Pretends to be ${f.substringAfter(':')} but isn't its official site",
            "${f.substringAfter(':')}-এর নাম ব্যবহার করছে, কিন্তু অফিসিয়াল সাইট নয়")
        f == "userinfo_trick" -> t("Hides the real address behind an @", "@ চিহ্নের পেছনে আসল ঠিকানা লুকানো")
        else -> f
    }

    val tabCheck get() = t("Check", "যাচাই")
    val tabLinks get() = t("Links", "লিংক")
    val tabLearn get() = t("Learn", "জানুন")
    val tabSettings get() = t("Settings", "সেটিংস")
}

/** Common scams in Bangladesh, written for people who are not technical. */
data class Guide(val en: String, val bn: String, val signsEn: String, val signsBn: String, val doEn: String, val doBn: String)

val GUIDES = listOf(
    Guide("Fake OTP / PIN request", "ভুয়া OTP / পিন চাওয়া",
        "Someone calls or texts saying they are from bKash, Nagad, a bank or your operator and asks for a code.",
        "কেউ বিকাশ, নগদ, ব্যাংক বা অপারেটরের লোক পরিচয় দিয়ে ফোন/মেসেজ করে কোড চায়।",
        "Never share a PIN or OTP. No company asks for it. Hang up and call the number in the official app.",
        "কখনো পিন বা OTP দেবেন না। কোনো প্রতিষ্ঠান তা চায় না। ফোন রেখে অফিসিয়াল অ্যাপের নম্বরে কল করুন।"),
    Guide("\"Money sent by mistake\"", "\"ভুল করে টাকা চলে গেছে\"",
        "A message says money reached you by mistake, then a call begs you to send it back.",
        "মেসেজে বলে ভুল করে আপনার কাছে টাকা গেছে, তারপর ফোন করে ফেরত চায়।",
        "Open your official app and check your balance. Fake SMS can look exactly like the real one.",
        "অফিসিয়াল অ্যাপ খুলে ব্যালেন্স দেখুন। ভুয়া SMS হুবহু আসলের মতো হতে পারে।"),
    Guide("Prize or lottery win", "পুরস্কার বা লটারি জেতা",
        "You \"won\" something you never entered, and must pay a fee or share details to claim it.",
        "আপনি এমন কিছু \"জিতেছেন\" যাতে অংশই নেননি, আর পেতে হলে ফি দিতে বা তথ্য দিতে হবে।",
        "Real prizes don't ask you to pay first. Ignore and delete.",
        "আসল পুরস্কারে আগে টাকা দিতে হয় না। উপেক্ষা করে মুছে ফেলুন।"),
    Guide("Job or easy income offers", "চাকরি বা সহজ আয়ের অফার",
        "Work from home, daily income, \"like videos and earn\", but first a registration fee.",
        "ঘরে বসে কাজ, দৈনিক আয়, \"ভিডিও লাইক করে আয়\", কিন্তু আগে রেজিস্ট্রেশন ফি।",
        "Don't pay to get a job. Look the company up yourself.",
        "চাকরি পেতে টাকা দেবেন না। প্রতিষ্ঠানটি নিজে খোঁজ নিয়ে দেখুন।"),
    Guide("Account will be blocked", "অ্যাকাউন্ট বন্ধ হয়ে যাবে",
        "Threatens that your SIM, bank or MFS account will close unless you act now or click a link.",
        "ভয় দেখায় যে এখনই কিছু না করলে বা লিংকে না গেলে সিম, ব্যাংক বা MFS অ্যাকাউন্ট বন্ধ হবে।",
        "Pressure is the trick. Check in the official app or at a branch.",
        "চাপ দেওয়াটাই কৌশল। অফিসিয়াল অ্যাপে বা শাখায় গিয়ে যাচাই করুন।"),
    Guide("Betting and casino links", "বেটিং ও ক্যাসিনো লিংক",
        "Bonus offers for betting apps, often over Telegram or WhatsApp, with short links.",
        "বেটিং অ্যাপের বোনাস অফার, প্রায়ই টেলিগ্রাম বা হোয়াটসঅ্যাপে, শর্ট লিংকসহ।",
        "Online gambling is illegal in Bangladesh and these sites often take deposits and vanish.",
        "বাংলাদেশে অনলাইন জুয়া অবৈধ, আর এসব সাইট প্রায়ই টাকা নিয়ে উধাও হয়।"),
)
