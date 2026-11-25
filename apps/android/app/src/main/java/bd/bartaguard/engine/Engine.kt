package bd.bartaguard.engine

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.regex.Pattern
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

// On-device port of ml/src/bartaguard/{text,redflags,verdict}.py and the TF-IDF + logistic
// regression model, kept line-for-line with the Swift engine in apps/ios/BartaGuardCore.
// Nothing here touches the network. Golden vectors from Python keep all three in step.

// region Text

object TextNorm {
    private fun isSpace(c: Int) = Character.isWhitespace(c) || Character.isSpaceChar(c)

    /** NFC, lowercase, Bangla digits -> ASCII, digit runs -> "0", collapse whitespace. */
    fun normalize(text: String): String {
        val lowered = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase()
        val out = StringBuilder()
        var inDigits = false
        var pendingSpace = false
        lowered.codePoints().forEach { s ->
            val c = if (s in 0x09E6..0x09EF) '0'.code + (s - 0x09E6) else s
            if (isSpace(c)) {
                pendingSpace = out.isNotEmpty()
                inDigits = false
                return@forEach
            }
            if (pendingSpace) { out.append(' '); pendingSpace = false }
            if (c in '0'.code..'9'.code) {
                if (!inDigits) out.append('0')
                inDigits = true
            } else {
                inDigits = false
                out.appendCodePoint(c)
            }
        }
        return out.toString()
    }

    private fun isWordScalar(s: Int) = s in 0x0980..0x09FF || s in 'a'.code..'z'.code || s in '0'.code..'9'.code

    fun words(text: String): List<String> {
        val words = mutableListOf<String>()
        val cur = StringBuilder()
        normalize(text).codePoints().forEach { s ->
            if (isWordScalar(s)) cur.appendCodePoint(s)
            else if (cur.isNotEmpty()) { words += cur.toString(); cur.setLength(0) }
        }
        if (cur.isNotEmpty()) words += cur.toString()
        return words
    }

    fun wordTokens(text: String): List<String> {
        val w = words(text)
        if (w.size <= 1) return w
        return w + (0 until w.size - 1).map { "${w[it]} ${w[it + 1]}" }
    }

    /** sklearn "char_wb" n-grams over code points (Python indexes code points, not UTF-16 units). */
    fun charNgrams(text: String, lo: Int = 2, hi: Int = 4): List<String> {
        val out = mutableListOf<String>()
        for (word in normalize(text).split(' ').filter { it.isNotEmpty() }) {
            val p = " $word ".codePoints().toArray()
            for (n in lo..hi) {
                if (p.size < n) continue
                for (i in 0..p.size - n) out += String(p, i, n)
            }
        }
        return out
    }
}

// endregion
// region Model

class LinearModel(json: String) {
    private class Block(val vocab: Map<String, Int>, val idf: DoubleArray)

    val labels: List<String>
    private val word: Block
    private val char: Block
    private val nWord: Int
    private val intercept: DoubleArray
    private val coef: List<Map<Int, Double>>

    init {
        val p = JSONObject(json)
        labels = p.getJSONArray("labels").strings()
        word = block(p.getJSONObject("word"))
        char = block(p.getJSONObject("char"))
        nWord = p.getInt("word_feature_count")
        intercept = p.getJSONArray("intercept").doubles()
        val c = p.getJSONArray("coef")
        coef = (0 until c.length()).map { k ->
            val o = c.getJSONObject(k)
            HashMap<Int, Double>(o.length() * 2).apply { o.keys().forEach { put(it.toInt(), o.getDouble(it)) } }
        }
    }

    private fun block(o: JSONObject): Block {
        val v = o.getJSONObject("vocab")
        val vocab = HashMap<String, Int>(v.length() * 2).apply { v.keys().forEach { put(it, v.getInt(it)) } }
        return Block(vocab, o.getJSONArray("idf").doubles())
    }

    /** sublinear tf (1 + ln count) x idf, then L2-normalised, as sklearn's TfidfVectorizer does per block. */
    private fun tfidf(tokens: List<String>, b: Block, offset: Int): Map<Int, Double> {
        val counts = HashMap<Int, Int>()
        for (t in tokens) b.vocab[t]?.let { counts[it] = (counts[it] ?: 0) + 1 }
        val v = HashMap<Int, Double>()
        var norm = 0.0
        for ((i, c) in counts) {
            val x = (1 + ln(c.toDouble())) * b.idf[i]
            v[i + offset] = x
            norm += x * x
        }
        norm = sqrt(norm)
        if (norm > 0) for (k in v.keys) v[k] = v[k]!! / norm
        return v
    }

    fun predictProba(text: String): Map<String, Double> {
        val x = tfidf(TextNorm.wordTokens(text), word, 0) + tfidf(TextNorm.charNgrams(text), char, nWord)
        val scores = labels.indices.map { k -> intercept[k] + x.entries.sumOf { (i, v) -> (coef[k][i] ?: 0.0) * v } }
        val m = scores.max()
        val e = scores.map { exp(it - m) }
        val z = e.sum()
        return labels.zip(e.map { it / z }).toMap()
    }
}

// endregion
// region Red flags

data class Flag(val key: String, val severity: Int, val en: String, val bn: String, val spans: List<IntRange>, val link: UrlInfo? = null)

data class UrlInfo(val url: String, val host: String, val flags: List<String>)

class RuleSet(json: String) {
    private class Rule(val key: String, val severity: Int, val en: String, val bn: String, val patterns: List<Pattern>)

    private val rules: List<Rule>
    private val dontShare: List<Pattern>
    private val shorteners: Set<String>
    private val riskyTlds: Set<String>
    private val brands: Map<String, List<String>>
    private val urlRegex: Pattern

    init {
        val p = JSONObject(json)
        val r = p.getJSONArray("rules")
        rules = (0 until r.length()).map { i ->
            val o = r.getJSONObject(i)
            Rule(o.getString("key"), o.getInt("severity"), o.getString("en"), o.getString("bn"),
                o.getJSONArray("patterns").strings().map(::compile))
        }
        dontShare = p.getJSONArray("dont_share_patterns").strings().map(::compile)
        shorteners = p.getJSONArray("shorteners").strings().toSet()
        riskyTlds = p.getJSONArray("risky_tlds").strings().toSet()
        val b = p.getJSONObject("brands")
        brands = b.keys().asSequence().associateWith { b.getJSONArray(it).strings() }
        urlRegex = compile(p.getString("url_pattern"))
    }

    // Python's str regexes are Unicode-aware for \d, \s, \b and case folding. Desktop Java needs
    // UNICODE_CHARACTER_CLASS for that; Android's ICU regex already behaves this way and rejects the flag.
    private fun compile(s: String): Pattern = try {
        Pattern.compile(s, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.UNICODE_CHARACTER_CLASS)
    } catch (e: IllegalArgumentException) {
        Pattern.compile(s, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
    }

    private fun matches(re: Pattern, t: String): List<IntRange> {
        val m = re.matcher(t)
        val out = mutableListOf<IntRange>()
        while (m.find()) if (m.end() > m.start()) out += m.start() until m.end()
        return out
    }

    /** Local heuristics only: parse, never fetch. Mirrors urllib.parse.urlsplit closely enough for hosts. */
    fun inspect(url: String): UrlInfo {
        val u = if ("://" in url) url else "http://$url"
        val scheme = u.substringBefore("://").lowercase()
        val netloc = u.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
        val hostPort = netloc.substringAfterLast('@')
        val host = (if (hostPort.startsWith("[")) hostPort.substringAfter('[').substringBefore(']')
                    else hostPort.substringBefore(':')).lowercase()
        val f = mutableListOf<String>()
        if (host in shorteners) f += "shortener"
        if (host.startsWith("xn--") || ".xn--" in host) f += "punycode"
        if (Regex("""\d+\.\d+\.\d+\.\d+""").matches(host)) f += "ip_address"
        val tld = if ('.' in host) host.substringAfterLast('.') else ""
        if (tld in riskyTlds) f += "risky_tld"
        if (scheme == "http") f += "not_https"
        for ((brand, official) in brands.entries.sortedBy { it.key }) {
            if (brand in host.replace("-", "") && official.none { host == it || host.endsWith(".$it") }) {
                f += "brand_lookalike:$brand"
            }
        }
        if ('@' in netloc) f += "userinfo_trick"
        return UrlInfo(url, host, f)
    }

    fun scan(text: String): List<Flag> {
        val t = Normalizer.normalize(text, Normalizer.Form.NFC)
        val protective = dontShare.any { matches(it, t).isNotEmpty() }
        val out = mutableListOf<Flag>()
        for (r in rules) {
            val spans = r.patterns.flatMap { matches(it, t) }
            if (spans.isEmpty()) continue
            if (r.key == "credential_request" && protective) {
                out += Flag("code_delivery", 0,
                    "Looks like a code being sent to you with a warning not to share it. Never give this code to anyone who calls or messages you.",
                    "মনে হচ্ছে আপনাকে একটি কোড পাঠানো হয়েছে এবং শেয়ার না করতে সতর্ক করা হয়েছে। কেউ ফোন বা মেসেজ করে চাইলে এই কোড দেবেন না।",
                    spans)
                continue
            }
            out += Flag(r.key, r.severity, r.en, r.bn, spans)
        }
        for (span in matches(urlRegex, t)) {
            val info = inspect(t.substring(span.first, span.last + 1))
            if (info.flags.isEmpty()) continue
            val list = info.flags.joinToString(", ")
            out += Flag("suspicious_link", if ((info.flags.toSet() - "not_https").isEmpty()) 1 else 2,
                "Link looks suspicious ($list). Do not open it; type the official address yourself.",
                "লিংকটি সন্দেহজনক ($list)। এটি খুলবেন না; অফিসিয়াল ঠিকানা নিজে টাইপ করুন।",
                listOf(span), info)
        }
        return out.sortedByDescending { it.severity }
    }
}

// endregion
// region Verdict

enum class Verdict { NORMAL, PROMOTIONAL, SUSPICIOUS, UNCERTAIN;
    val wire get() = name.lowercase()
}

data class Analysis(
    val verdict: Verdict,
    val flags: List<Flag>,
    val modelLabel: String?,
    val modelConfidence: Double?,
    val outOfDomain: Boolean,
)

class BartaGuardEngine(modelJson: String, rulesJson: String, val threshold: Double = 0.6) {
    private val model = LinearModel(modelJson)
    private val rules = RuleSet(rulesJson)

    companion object {
        const val MIN_BANGLA_SHARE = 0.5
        const val MIN_LETTERS = 12

        fun inDomain(text: String): Boolean {
            var bangla = 0
            var latin = 0
            text.codePoints().forEach { s ->
                if (s in 0x0980..0x09FF) bangla++ else if (s in 'a'.code..'z'.code || s in 'A'.code..'Z'.code) latin++
            }
            val letters = bangla + latin
            return letters >= MIN_LETTERS && bangla.toDouble() / maxOf(letters, 1) >= MIN_BANGLA_SHARE
        }
    }

    fun probabilities(text: String) = model.predictProba(text)
    fun flags(text: String) = rules.scan(text)
    fun inspect(url: String) = rules.inspect(url)

    fun analyze(text: String): Analysis {
        val flags = rules.scan(text)
        val top = flags.maxOfOrNull { it.severity } ?: 0
        val inDom = inDomain(text)
        var label: String? = null
        var conf: Double? = null
        if (inDom) {
            model.predictProba(text).maxByOrNull { it.value }?.let { label = it.key; conf = it.value }
        }
        val l = label
        val c = conf
        val verdict = when {
            top >= 3 -> Verdict.SUSPICIOUS
            l != null && c != null && c >= threshold -> when {
                l == "SPAM" || top >= 2 -> Verdict.SUSPICIOUS
                l == "HAM" -> Verdict.NORMAL
                else -> Verdict.PROMOTIONAL
            }
            top >= 2 -> Verdict.SUSPICIOUS
            else -> Verdict.UNCERTAIN
        }
        return Analysis(verdict, flags, label, conf, !inDom)
    }
}

// endregion

private fun JSONArray.strings() = (0 until length()).map { getString(it) }
private fun JSONArray.doubles() = DoubleArray(length()) { getDouble(it) }
