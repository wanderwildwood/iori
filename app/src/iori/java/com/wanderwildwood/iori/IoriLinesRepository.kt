package com.wanderwildwood.iori

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import com.github.gezimos.inkos.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The home screen's lines, read from Glance (hitome), which hands over what the lock screen
 * would show from the apps that take part, and why each source shows or does not.
 *
 * Everything here runs on its own scope on the IO dispatcher, and every read is wrapped: a
 * timeout with a [CancellationSignal], a catch, `cursor.use`, and a check on each column. What
 * reaches composition is only finished Strings in a [StateFlow], which by its nature passes a
 * value on only when it differs from the last one, so an unchanged answer redraws nothing on
 * the panel.
 */
object IoriLinesRepository {

    const val GLANCE = "com.wanderwildwood.hitome"
    val PANEL: Uri = Uri.parse("content://com.wanderwildwood.hitome.panel/lines")

    /** The version of Glance that first answers [PANEL], as it is said to the reader. */
    const val GLANCE_NEEDED = "0.1.24"

    /** Lines drawn on home at most. */
    const val MAX_LINES = 3

    private const val QUERY_TIMEOUT_MS = 1_500L
    private const val DEBOUNCE_MS = 1_000L

    /** One line, ready to draw. */
    data class Line(val pkg: String, val lead: String, val text: String, val bold: Boolean)

    /** Why a source shows on home, or why it does not. */
    enum class Status { ON, OFF_HERE, NOT_INSTALLED, NEEDS_UPDATE, OFF_APP, OFF_GLANCE, NO_ANSWER }

    /** Glance itself: answering, absent, too old for [PANEL], or not answering. */
    enum class GlanceState { UNKNOWN, OK, NOT_INSTALLED, NEEDS_UPDATE, NO_ANSWER }

    data class Source(val pkg: String, val label: String, val status: Status)

    data class Snapshot(
        val glance: GlanceState = GlanceState.UNKNOWN,
        val sources: List<Source> = emptyList(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Context>(Channel.CONFLATED)
    private var debounce: Job? = null
    private val observed = HashSet<String>()
    @Volatile private var started = false

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    /** Ask again now. Requests that arrive while a read is running fold into one more read. */
    fun refresh(context: Context) {
        start()
        requests.trySend(context.applicationContext)
    }

    /** Ask again once things have been quiet for [DEBOUNCE_MS]; for change notifications. */
    fun refreshSoon(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            debounce?.cancel()
            debounce = scope.launch {
                delay(DEBOUNCE_MS)
                refresh(app)
            }
        }
    }

    private fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            scope.launch {
                for (context in requests) {
                    try {
                        read(context)
                    } catch (_: Exception) {
                        // A read that failed in a way not caught below leaves the last answer up.
                    }
                }
            }
        }
    }

    private fun read(context: Context) {
        val pm = context.packageManager
        val glance = glanceState(pm)
        if (glance != GlanceState.OK) {
            publish(Snapshot(glance = glance), emptyList())
            return
        }
        val rows = queryPanel(context)
        if (rows == null) {
            publish(Snapshot(glance = GlanceState.NO_ANSWER), emptyList())
            return
        }
        val prefs = Iori.prefs(context)
        val byPkg = LinkedHashMap<String, MutableList<Row>>()
        rows.forEach { byPkg.getOrPut(it.pkg) { mutableListOf() }.add(it) }

        val sources = ArrayList<Source>()
        val lines = ArrayList<Line>()
        for ((pkg, own) in byPkg) {
            val label = label(pm, pkg)
            val status = try {
                fault(context, pkg)
                val given = statusOf(own.first().status)
                if (given == Status.ON && !prefs.getBoolean(Iori.lineKey(pkg), true)) Status.OFF_HERE else given
            } catch (_: Exception) {
                Status.NO_ANSWER
            }
            sources += Source(pkg, label, status)
            if (status == Status.ON) {
                own.filter { it.text.isNotBlank() }.forEach { lines += Line(pkg, it.lead, it.text, it.bold) }
            }
            observe(context, pkg)
        }
        publish(Snapshot(GlanceState.OK, sources), lines.take(MAX_LINES))
    }

    private fun publish(snapshot: Snapshot, lines: List<Line>) {
        _snapshot.value = snapshot
        _lines.value = lines
    }

    private fun glanceState(pm: PackageManager): GlanceState {
        return try {
            pm.getPackageInfo(GLANCE, 0)
            if (pm.resolveContentProvider(PANEL.authority!!, 0) == null) GlanceState.NEEDS_UPDATE else GlanceState.OK
        } catch (_: PackageManager.NameNotFoundException) {
            GlanceState.NOT_INSTALLED
        } catch (_: Exception) {
            GlanceState.NO_ANSWER
        }
    }

    private class Row(val pkg: String, val lead: String, val text: String, val bold: Boolean, val status: String)

    /** The panel's rows, or null when Glance refused, failed or took too long. */
    private fun queryPanel(context: Context): List<Row>? {
        val signal = CancellationSignal()
        val timer = scope.launch {
            delay(QUERY_TIMEOUT_MS)
            signal.cancel()
        }
        return try {
            context.contentResolver.query(PANEL, null, null, null, null, signal)?.use { rowsOf(it) }
        } catch (_: OperationCanceledException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            timer.cancel()
        }
    }

    private fun rowsOf(c: Cursor): List<Row>? {
        val pkg = c.getColumnIndex("pkg")
        val lead = c.getColumnIndex("lead")
        val text = c.getColumnIndex("text")
        val bold = c.getColumnIndex("bold")
        val status = c.getColumnIndex("status")
        if (pkg < 0 || text < 0 || status < 0) return null
        val out = ArrayList<Row>()
        while (c.moveToNext()) {
            val p = c.getString(pkg) ?: continue
            out += Row(
                pkg = p,
                lead = if (lead >= 0) c.getString(lead).orEmpty().oneLine() else "",
                text = c.getString(text).orEmpty().oneLine(),
                bold = bold >= 0 && !c.isNull(bold) && c.getInt(bold) != 0,
                status = c.getString(status).orEmpty(),
            )
        }
        return out
    }

    private fun String.oneLine() = replace('\n', ' ').replace('\r', ' ').trim()

    private fun statusOf(value: String): Status = when (value) {
        "on" -> Status.ON
        "off_app" -> Status.OFF_APP
        "off_glance" -> Status.OFF_GLANCE
        "not_installed" -> Status.NOT_INSTALLED
        "needs_update" -> Status.NEEDS_UPDATE
        else -> Status.NO_ANSWER
    }

    private fun label(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    /**
     * Each source's own provider says when its lines change; so does the panel. Every
     * registration is tried on its own, since a source may vanish between the read and here.
     */
    private fun observe(context: Context, pkg: String) {
        register(context, PANEL.toString())
        register(context, "content://$pkg.glance/lines")
    }

    private fun register(context: Context, uri: String) {
        synchronized(observed) {
            if (!observed.add(uri)) return
        }
        try {
            context.contentResolver.registerContentObserver(
                Uri.parse(uri),
                true,
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) = refreshSoon(context)
                },
            )
        } catch (_: Exception) {
            synchronized(observed) { observed.remove(uri) }
        }
    }

    /**
     * Debug builds only: makes a source throw, hang or come back empty, to show that one bad
     * source costs only its own line. A hang waits out the same timeout a read gets.
     */
    private fun fault(context: Context, pkg: String) {
        if (!BuildConfig.DEBUG) return
        when (Iori.prefs(context).getString(Iori.faultKey(pkg), null)) {
            Fault.THROW -> throw IllegalStateException("fault injected for $pkg")
            Fault.NULL -> throw NullPointerException("fault injected for $pkg")
            Fault.HANG -> runBlocking {
                withTimeoutOrNull(QUERY_TIMEOUT_MS) { delay(Long.MAX_VALUE) }
                throw OperationCanceledException("fault injected for $pkg")
            }
        }
    }
}

/** The faults a debug build can put on a source, in the order the lines dialog steps through. */
object Fault {
    const val THROW = "throw"
    const val HANG = "hang"
    const val NULL = "null"
    val CYCLE = listOf<String?>(null, THROW, HANG, NULL)
}

/** The faults a debug build can throw from home itself. */
object HomeFault {
    /** Thrown from iori's own composable: the guard should turn lines off. */
    const val OWN = "own"
    /** Thrown with upstream frames only: the guard should leave lines alone. */
    const val UPSTREAM = "upstream"
    val CYCLE = listOf<String?>(null, OWN, UPSTREAM)
}
