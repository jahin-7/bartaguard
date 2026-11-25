package bd.bartaguard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bd.bartaguard.engine.Analysis
import bd.bartaguard.engine.BartaGuardEngine
import bd.bartaguard.engine.UrlInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private var engine: BartaGuardEngine? = null

    var ready by mutableStateOf(false); private set
    var lang by mutableStateOf(prefs.lang); private set
    var tab by mutableStateOf(0)
    var input by mutableStateOf("")
    var checked by mutableStateOf<Pair<String, Analysis>?>(null); private set
    var fromWatch by mutableStateOf(false); private set
    var linkInput by mutableStateOf("")
    var link by mutableStateOf<UrlInfo?>(null); private set
    var watch by mutableStateOf(prefs.watch); private set
    var access by mutableStateOf(false); private set
    var keepHistory by mutableStateOf(prefs.keepHistory); private set
    var history by mutableStateOf(prefs.history()); private set

    init {
        viewModelScope.launch {
            engine = withContext(Dispatchers.Default) { Engines.get(getApplication()) }
            ready = true
            if (input.isNotBlank() && checked == null) check("share")
        }
    }

    fun setLanguage(l: String) { prefs.lang = l; lang = l }

    /** Text arriving from the share sheet, the text-selection menu, or a scam warning. */
    fun receive(text: String, viaWatch: Boolean) {
        input = text.trim()
        fromWatch = viaWatch
        tab = 0
        if (ready) check(if (viaWatch) "auto" else "share")
    }

    fun check(source: String = "paste") {
        val e = engine ?: return
        val text = input.trim().takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            val a = withContext(Dispatchers.Default) { e.analyze(text) }
            // Flag spans index the NFC form, so show that form.
            checked = Normalizer.normalize(text, Normalizer.Form.NFC) to a
            if (source != "auto") prefs.save(a.verdict, text, source)
            history = prefs.history()
        }
    }

    fun reset() { checked = null; input = ""; fromWatch = false }

    fun inspect() {
        val e = engine ?: return
        val u = linkInput.trim().takeIf { it.isNotEmpty() } ?: return
        link = e.inspect(u)
    }

    fun refreshAccess() {
        access = WatchService.hasAccess(getApplication())
        history = prefs.history()
    }

    fun setWatching(on: Boolean) { prefs.watch = on; watch = on }
    fun setHistory(on: Boolean) { prefs.keepHistory = on; keepHistory = on; history = prefs.history() }
    fun clearHistory() { prefs.clearHistory(); history = emptyList() }
}
