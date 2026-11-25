package bd.bartaguard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import bd.bartaguard.engine.BartaGuardEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Same golden cases as the JVM test, but on Android's own regex (ICU) and text libraries. */
@RunWith(AndroidJUnit4::class)
class DeviceGoldenTest {
    @Test fun matchesPythonOnDevice() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val golden = JSONObject(inst.context.assets.open("ondevice_golden.json").bufferedReader().readText())
        val engine = Engines.get(inst.targetContext)
        val cases = golden.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val text = c.getString("text")
            val p = engine.probabilities(text)
            listOf("HAM", "PROMO", "SPAM").forEachIndexed { k, l ->
                assertTrue("$l for: ${text.take(50)}", kotlin.math.abs(p[l]!! - c.getJSONArray("proba").getDouble(k)) < 2e-3)
            }
            val want = c.getJSONArray("flags").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals(text.take(60), want, engine.flags(text).map { it.key }.sorted())
            assertEquals(text.take(60), c.getBoolean("in_domain"), BartaGuardEngine.inDomain(text))
            assertEquals(text.take(60), c.getString("verdict"), engine.analyze(text).verdict.wire)
        }
    }
}
