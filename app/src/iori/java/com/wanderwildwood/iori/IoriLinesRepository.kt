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
 * Everything here runs on its own scope on the IO dispatcher, and every read is wrapped: an
 * unstable provider client, so Glance dying mid-read costs only that read and never the
 * launcher; a [CancellationSignal] cancelled after [QUERY_TIMEOUT_MS], which Glance watches and
 * which ends its own wait early; a catch; `cursor.use`; and a check on each column. A Glance
 * that is slow to start is bounded only by that same signal, and only once it is running. What
 * reaches composition is only short finished Strings in a [StateFlow], which by its nature
 * passes a value on only when it differs from the last one, so an unchanged answer redraws
 * nothing on the panel.
 */
object IoriLinesRepository {

    const val GLANCE = "com.wanderwildwood.hitome"

    /**
     * SHA-256 of the certificate Glance's releases are signed with: the one in
     * assets/iori_apps.json (IoriAppsTest holds the two the same), read again from its v0.1.56
     * release with apksigner. Lines are read only from a panel held by Glance signed with this.
     */
    const val GLANCE_CERT = "8b5ad77e1b6c23ad805c7aef9864967bfefbafd442e43a472ab212f0593248b8"
    val PANEL: Uri = Uri.parse("content://com.wanderwildwood.hitome.panel/lines")

    /** The version of Glance that first answers [PANEL], as it is said to the reader. */
    const val GLANCE_NEEDED = "0.1.24"

    /** Lines drawn on home at most. */
    const val MAX_LINES = 3

    private const val QUERY_TIMEOUT_MS = 1_500L
    private const val DEBOUNCE_MS = 1_000L

    /** However often sources say they changed, home reads again at least this often. */
    private const val DEBOUNCE_MAX_WAIT_MS = 5_000L

    /** The longest lead and text that reach composition; the row ellipsises well before. */
    private const val MAX_LEAD = 24
    private const val MAX_TEXT = 160

    /** One line, ready to draw. */
    data class Line(val pkg: String, val lead: String, val text: String, val bold: Boolean)

    /** Why a source shows on home, or why it does not. */
    enum class Status { ON, OFF_HERE, NOT_INSTALLED, NEEDS_UPDATE, OFF_APP, OFF_GLANCE, NO_ANSWER }

    /**
     * Glance itself: answering, absent, too old for [PANEL], refusing this build (it answers
     * only builds signed with the key it knows), or not answering.
     */
    enum class GlanceState { UNKNOWN, OK, NOT_INSTALLED, NEEDS_UPDATE, REFUSED, NO_ANSWER }

    data class Source(val pkg: String, val label: String, val status: Status)

    data class Snapshot(
        val glance: GlanceState = GlanceState.UNKNOWN,
        val sources: List<Source> = emptyList(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Context>(Channel.CONFLATED)
    private var debounce: Job? = null
    /** When the oldest change not yet read arrived, in uptime milliseconds; 0 when none. */
    private var pendingSince = 0L
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

    /**
     * Ask again once things have been quiet for [DEBOUNCE_MS]; for change notifications. A
     * source that keeps saying it changed cannot put the read off for longer than
     * [DEBOUNCE_MAX_WAIT_MS]: once the oldest change waiting is that old, the read already
     * waiting is left to run.
     */
    fun refreshSoon(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            val now = android.os.SystemClock.uptimeMillis()
            if (pendingSince == 0L) pendingSince = now
            val pending = debounce
            if (pending != null && pending.isActive && now - pendingSince >= DEBOUNCE_MAX_WAIT_MS) return
            pending?.cancel()
            debounce = scope.launch {
                delay(DEBOUNCE_MS)
                synchronized(this@IoriLinesRepository) { pendingSince = 0L }
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
        val rows = when (val answer = queryPanel(context)) {
            // Asked again after the read: the panel could have changed hands while it ran.
            is PanelAnswer.Rows -> if (glanceState(pm) == GlanceState.OK) answer.rows else {
                publish(Snapshot(glance = GlanceState.NO_ANSWER), emptyList())
                return
            }
            PanelAnswer.Refused -> {
                publish(Snapshot(glance = GlanceState.REFUSED), emptyList())
                return
            }
            PanelAnswer.NoAnswer -> {
                publish(Snapshot(glance = GlanceState.NO_ANSWER), emptyList())
                return
            }
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

    /**
     * Whether Glance is there and holds [PANEL]. The panel's holder must be Glance by name and
     * signed with [GLANCE_CERT] alone: on a phone with an old Glance (no panel yet) any app can
     * take the authority, and its lines would be drawn on home and open what they name.
     */
    private fun glanceState(pm: PackageManager): GlanceState {
        return try {
            pm.getPackageInfo(GLANCE, 0)
            val owner = pm.resolveContentProvider(PANEL.authority!!, 0)?.packageName ?: return GlanceState.NEEDS_UPDATE
            if (isGlance(owner, signers(pm, owner), BuildConfig.DEBUG)) GlanceState.OK else GlanceState.NO_ANSWER
        } catch (_: PackageManager.NameNotFoundException) {
            GlanceState.NOT_INSTALLED
        } catch (_: Exception) {
            GlanceState.NO_ANSWER
        }
    }

    /** The SHA-256 of each certificate [pkg] is signed with, lower-case hex; none when it cannot be read. */
    @Suppress("DEPRECATION")
    private fun signers(pm: PackageManager, pkg: String): List<String> = runCatching {
        val raw = if (android.os.Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        raw.orEmpty().map { sig ->
            java.security.MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }.getOrDefault(emptyList())

    private class Row(val pkg: String, val lead: String, val text: String, val bold: Boolean, val status: String)

    /** What the panel said: its rows, a refusal of this build, or nothing in time. */
    private sealed interface PanelAnswer {
        class Rows(val rows: List<Row>) : PanelAnswer
        /** Glance answered with no cursor at all: it does not hand lines to this build. */
        data object Refused : PanelAnswer
        /** Glance failed, died, took too long, or answered in a shape not understood. */
        data object NoAnswer : PanelAnswer
    }

    private fun queryPanel(context: Context): PanelAnswer {
        val signal = CancellationSignal()
        val timer = scope.launch {
            delay(QUERY_TIMEOUT_MS)
            signal.cancel()
        }
        // Unstable: a stable reference would have the system end the launcher with Glance, should
        // Glance be killed or updated while the cursor is open.
        val client = try {
            context.contentResolver.acquireUnstableContentProviderClient(PANEL.authority!!)
        } catch (_: Exception) {
            null
        }
        if (client == null) {
            timer.cancel()
            return PanelAnswer.NoAnswer
        }
        return try {
            val cursor = client.query(PANEL, null, null, null, null, signal)
            if (cursor == null) {
                PanelAnswer.Refused
            } else {
                cursor.use { c -> rowsOf(c)?.let { PanelAnswer.Rows(it) } ?: PanelAnswer.NoAnswer }
            }
        } catch (_: OperationCanceledException) {
            PanelAnswer.NoAnswer
        } catch (_: Exception) {
            PanelAnswer.NoAnswer
        } finally {
            timer.cancel()
            try {
                client.close()
            } catch (_: Exception) {
            }
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
                lead = if (lead >= 0) c.getString(lead).orEmpty().oneLine().take(MAX_LEAD) else "",
                text = c.getString(text).orEmpty().oneLine().take(MAX_TEXT),
                bold = bold >= 0 && !c.isNull(bold) && c.getInt(bold) != 0,
                status = c.getString(status).orEmpty(),
            )
        }
        return out
    }

    private fun String.oneLine() = replace('\n', ' ').replace('\r', ' ').trim()

    /**
     * Starts loading iori's preferences file on the IO dispatcher, so that home's first look at
     * it (whether lines were turned off after a crash) finds it already read.
     */
    fun warm(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                Iori.prefs(app).contains(Iori.KEY_TRIPPED)
            } catch (_: Exception) {
            }
        }
    }

    private fun statusOf(value: String): Status = when (value) {
        "on" -> Status.ON
        "off_app" -> Status.OFF_APP
        "off_glance" -> Status.OFF_GLANCE
        "not_installed" -> Status.NOT_INSTALLED
        "needs_update" -> Status.NEEDS_UPDATE
        else -> Status.NO_ANSWER
    }

    /** The name its launcher shows, whether or not it is installed. See [IoriNames.shown]. */
    private fun label(pm: PackageManager, pkg: String): String = IoriNames.shown(
        pkg,
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            null
        },
    )

    /**
     * Each source's own provider says when its lines change; so does the panel, when its order,
     * a switch in Glance, or today's events change there. Every
     * registration is tried on its own, since a source may vanish between the read and here.
     */
    private fun observe(context: Context, pkg: String) {
        register(context, PANEL.toString())
        IoriNames.providers(pkg).forEach { register(context, "content://$it.glance/lines") }
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

/**
 * Whether the app [owner], signed with [signers], is Glance: its name, and Glance's release
 * certificate and nothing else (a debug Home also takes Glance signed with a debug key). No
 * certificate read is a refusal. Outside the object so the tests reach it without Android.
 */
internal fun isGlance(owner: String?, signers: List<String>, debugBuild: Boolean): Boolean {
    if (owner != IoriLinesRepository.GLANCE || signers.isEmpty()) return false
    val certs = if (debugBuild) IoriAccount.DEBUG_CERTS + IoriLinesRepository.GLANCE_CERT else setOf(IoriLinesRepository.GLANCE_CERT)
    return signers.all { it.lowercase() in certs }
}
