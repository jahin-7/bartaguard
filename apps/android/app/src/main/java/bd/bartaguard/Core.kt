package bd.bartaguard

import android.content.Context
import bd.bartaguard.engine.BartaGuardEngine
import bd.bartaguard.engine.Verdict
import org.json.JSONArray
import org.json.JSONObject

/** One engine per process, shared by the UI and the notification watcher. Loading takes ~1 s. */
object Engines {
    @Volatile private var engine: BartaGuardEngine? = null

    fun get(ctx: Context): BartaGuardEngine = engine ?: synchronized(this) {
        engine ?: BartaGuardEngine(
            ctx.assets.open("ondevice_model.json").bufferedReader().readText(),
            ctx.assets.open("redflags_rules.json").bufferedReader().readText(),
        ).also { engine = it }
    }
}

data class SavedCheck(val time: Long, val verdict: Verdict, val preview: String, val source: String)

/**
 * Everything stays on the phone. History is off by default and keeps only a short preview.
 * Plain SharedPreferences so the notification service can read the switch synchronously.
 */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("bartaguard", Context.MODE_PRIVATE)

    var lang: String?
        get() = sp.getString("lang", null)
        set(v) = sp.edit().putString("lang", v).apply()
    /** "system", "light" or "dark". */
    var appearance: String
        get() = sp.getString("appearance", "system") ?: "system"
        set(v) = sp.edit().putString("appearance", v).apply()
    var watch: Boolean
        get() = sp.getBoolean("watch", false)
        set(v) = sp.edit().putBoolean("watch", v).apply()
    var keepHistory: Boolean
        get() = sp.getBoolean("history", false)
        set(v) { sp.edit().putBoolean("history", v).apply(); if (!v) clearHistory() }

    fun history(): List<SavedCheck> {
        val a = JSONArray(sp.getString("checks", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            SavedCheck(it.getLong("t"), Verdict.valueOf(it.getString("v")), it.getString("p"), it.getString("s"))
        }
    }

    fun save(verdict: Verdict, text: String, source: String) {
        if (!keepHistory) return
        val items = listOf(SavedCheck(System.currentTimeMillis(), verdict, text.take(90), source)) + history().take(49)
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("t", it.time).put("v", it.verdict.name).put("p", it.preview).put("s", it.source)) }
        sp.edit().putString("checks", a.toString()).apply()
    }

    fun clearHistory() = sp.edit().remove("checks").apply()
}
