package bd.bartaguard

import bd.bartaguard.engine.BartaGuardEngine
import bd.bartaguard.engine.TextNorm
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Kotlin engine must agree with the Python training code on every golden case. */
class GoldenTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun asset(name: String) = File("src/main/assets/$name").readText()

    private val golden = JSONObject(res("ondevice_golden.json"))
    private val engine = BartaGuardEngine(asset("ondevice_model.json"), asset("redflags_rules.json"), golden.getDouble("threshold"))
    private val cases = golden.getJSONArray("cases").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private val labels = listOf("HAM", "PROMO", "SPAM")

    @Test fun probabilitiesMatchPython() {
        for (c in cases) {
            val text = c.getString("text")
            val p = engine.probabilities(text)
            labels.forEachIndexed { i, l ->
                val want = c.getJSONArray("proba").getDouble(i)
                assertTrue("$l ${p[l]} vs $want for: ${text.take(50)}", kotlin.math.abs(p[l]!! - want) < 2e-3)
            }
        }
    }

    @Test fun flagsMatchPython() {
        for (c in cases) {
            val want = c.getJSONArray("flags").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals(c.getString("text").take(60), want, engine.flags(c.getString("text")).map { it.key }.sorted())
        }
    }

    @Test fun verdictsMatchPython() {
        for (c in cases) {
            val text = c.getString("text")
            assertEquals(text.take(60), c.getBoolean("in_domain"), BartaGuardEngine.inDomain(text))
            assertEquals(text.take(60), c.getString("verdict"), engine.analyze(text).verdict.wire)
        }
    }

    @Test fun normalisation() {
        assertEquals("৳0: 0 জিবি", TextNorm.normalize("  ৳২২০:  ১৫ জিবি "))
        assertEquals(listOf("কিনতে", "ডায়াল"), TextNorm.words("কিনতে ডায়াল")) // vowel signs stay inside words
    }

    @Test fun linksAreInspectedNotFetched() {
        val info = engine.inspect("http://bkash-verify.xyz/login")
        assertTrue(info.flags.toString(), "brand_lookalike:bkash" in info.flags && "not_https" in info.flags)
        assertEquals(emptyList<String>(), engine.inspect("https://www.bkash.com").flags)
    }
}
