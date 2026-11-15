package com.wanderwildwood.iori

import com.github.gezimos.inkos.R
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * "Your apps": the apps Home is made to work with, whether each is on the phone, and getting
 * the ones that are not.
 *
 * The list ships inside Home (assets/iori_apps.json, written by tools/make-apps-list.py). Each
 * entry names the app's GitHub repository and the certificate its releases are signed with. An
 * app is fetched from its latest release there, and is handed to Android only when
 *   - the file's SHA-256 is the one published beside it,
 *   - the package inside is the one asked for, and
 *   - it is signed with the certificate in the list.
 * The last is the one that matters: the list is inside Home, so nothing downloaded can vouch
 * for itself.
 *
 * Home reaches the network for this and nothing else, and only when this page is opened or an
 * app is asked for.
 */
object IoriApps {

    private const val TAG = "IoriApps"

    data class App(
        val name: String,
        val pkg: String,
        val repo: String,
        val file: String,
        val cert: String,
        /** The section it is listed under, as `tools/make-apps-list.py` wrote it: a key of [SECTIONS]. */
        val section: String,
    ) {
        val core: Boolean get() = section == CORE
    }

    const val CORE = "core"

    /** The sections, in the order they are listed, each with its heading. The list file names them by key. */
    val SECTIONS: List<Pair<String, Int>> = listOf(
        CORE to R.string.iori_apps_core,
        "outdoors" to R.string.iori_apps_outdoors,
        "road" to R.string.iori_apps_road,
        "daily" to R.string.iori_apps_daily,
        "music" to R.string.iori_apps_music,
        "reading" to R.string.iori_apps_reading,
        "games" to R.string.iori_apps_games,
        "tools" to R.string.iori_apps_tools,
    )

    /**
     * [apps] under their headings, empty sections left out. An app whose section this build does
     * not know is listed under the last heading, so a newer list never hides an app.
     */
    fun sections(apps: List<App>): List<Pair<Int, List<App>>> {
        val known = SECTIONS.map { it.first }.toSet()
        val last = SECTIONS.last().first
        return SECTIONS.map { (key, head) ->
            head to apps.filter { it.section == key || (key == last && it.section !in known) }
        }.filter { it.second.isNotEmpty() }
    }

    enum class Step { IDLE, DOWNLOADING, CHECKING, INSTALLING, CONFIRM, FAILED }

    /** Why an install stopped, as the page says it. */
    enum class Failure { NOT_ALLOWED, NETWORK, CHECKSUM, WRONG_APP, WRONG_KEY, REFUSED }

    data class State(
        val installed: String? = null,
        val latest: String? = null,
        val step: Step = Step.IDLE,
        val percent: Int = 0,
        val failure: Failure? = null,
    ) {
        /** There is a newer release than the one on the phone. */
        val canUpdate: Boolean get() = installed != null && latest != null && newer(latest, installed)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lookups = Semaphore(4)
    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states

    @Volatile private var loaded: List<App>? = null

    fun apps(context: Context): List<App> = loaded ?: runCatching {
        val json = context.assets.open("iori_apps.json").bufferedReader().use { it.readText() }
        val array = JSONObject(json).getJSONArray("apps")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            App(
                name = o.getString("name"),
                pkg = o.getString("package"),
                repo = o.getString("repo"),
                file = o.getString("file"),
                cert = o.getString("cert").lowercase(),
                section = o.getString("section"),
            )
        }
    }.getOrElse {
        Log.w(TAG, "the app list could not be read", it)
        emptyList()
    }.also { loaded = it }

    fun find(context: Context, pkg: String): App? = apps(context).firstOrNull { it.pkg == pkg }

    private fun set(pkg: String, change: (State) -> State) {
        _states.update { all -> all + (pkg to change(all[pkg] ?: State())) }
    }

    private fun installedVersion(context: Context, pkg: String): String? = try {
        context.packageManager.getPackageInfo(pkg, 0).versionName ?: ""
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Which of the apps are on the phone, and at what version. No network. */
    fun refreshInstalled(context: Context) {
        val app = context.applicationContext
        apps(app).forEach { a ->
            val version = installedVersion(app, a.pkg)
            set(a.pkg) { it.copy(installed = version) }
        }
    }

    /** Asks GitHub for each installed app's latest release, to offer an update. */
    fun checkLatest(context: Context) {
        val app = context.applicationContext
        apps(app).filter { installedVersion(app, it.pkg) != null }.forEach { a ->
            scope.launch {
                lookups.withPermit {
                    val latest = runCatching { latestVersion(a) }.getOrNull()
                    if (latest != null) set(a.pkg) { it.copy(latest = latest) }
                }
            }
        }
    }

    /** The tag the "latest release" link forwards to, without its v. */
    private fun latestVersion(a: App): String? {
        val c = URL("https://github.com/${a.repo}/releases/latest").openConnection() as HttpURLConnection
        return try {
            c.instanceFollowRedirects = false
            c.requestMethod = "HEAD"
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.getHeaderField("Location")?.substringAfterLast("/tag/", "")?.removePrefix("v")?.takeIf { it.isNotBlank() }
        } finally {
            c.disconnect()
        }
    }

    /** True when [latest] is a higher version than [installed], by its numbers. */
    internal fun newer(latest: String, installed: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Whether the person has let Home install apps. */
    fun allowed(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens Android's own screen with the switch that lets Home install apps. */
    fun askToAllow(context: Context) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** What a first run offers ticked: the core set, less what is already on the phone. */
    internal fun firstPick(apps: List<App>, installed: Set<String>): Set<String> =
        apps.filter { it.core && it.pkg !in installed }.map { it.pkg }.toSet()

    private val queueLock = Any()
    private var worker: Job? = null
    private val _queue = MutableStateFlow<List<String>>(emptyList())

    /** The packages still to be got by [installAll]; the first is the one in hand. */
    val queue: StateFlow<List<String>> = _queue

    /**
     * Gets several apps one after the other, so Android's "install this app?" questions come
     * one at a time. An app that fails is passed over; the rest go on.
     */
    fun installAll(context: Context, list: List<App>) {
        val app = context.applicationContext
        synchronized(queueLock) {
            _queue.update { now -> now + list.map { it.pkg }.filter { it !in now } }
            if (worker != null) return
            worker = scope.launch {
                while (true) {
                    val pkg = synchronized(queueLock) {
                        _queue.value.firstOrNull().also { if (it == null) worker = null }
                    } ?: break
                    val a = find(app, pkg)
                    if (a != null) {
                        install(app, a)
                        // install() has set the step before it returns, so IDLE here means done.
                        val end = states.first { all ->
                            val step = all[pkg]?.step
                            step == Step.IDLE || step == Step.FAILED
                        }[pkg]
                        // Not allowed to install: the same answer waits for every other app.
                        if (end?.failure == Failure.NOT_ALLOWED) _queue.value = listOf(pkg)
                    }
                    _queue.update { it - pkg }
                }
            }
        }
    }

    /** Lets the app in hand finish and drops the ones waiting behind it. */
    fun stopQueue() {
        _queue.update { it.take(1) }
    }

    /** Fetches [a]'s latest release, checks it, and hands it to Android to install. */
    fun install(context: Context, a: App) {
        val app = context.applicationContext
        val now = _states.value[a.pkg]?.step
        if (now == Step.DOWNLOADING || now == Step.CHECKING || now == Step.INSTALLING) return
        // Android lets an app install others only once the person has allowed it, on a screen
        // of its own. Asked for before anything is downloaded: an install begun without it is
        // thrown away by Android even if the switch is then turned on.
        if (!allowed(app)) {
            set(a.pkg) { it.copy(step = Step.FAILED, failure = Failure.NOT_ALLOWED) }
            askToAllow(app)
            return
        }
        set(a.pkg) { it.copy(step = Step.DOWNLOADING, percent = 0, failure = null) }
        scope.launch {
            val dir = File(app.cacheDir, "iori-apps").apply { mkdirs() }
            val apk = File(dir, a.file)
            try {
                val base = "https://github.com/${a.repo}/releases/latest/download/${a.file}"
                val want = try {
                    URL("$base.sha256").readText().trim().substringBefore(' ').lowercase()
                } catch (e: Exception) {
                    return@launch fail(a, Failure.NETWORK, e)
                }
                try {
                    download(base, apk) { percent -> set(a.pkg) { it.copy(percent = percent) } }
                } catch (e: Exception) {
                    return@launch fail(a, Failure.NETWORK, e)
                }
                set(a.pkg) { it.copy(step = Step.CHECKING) }
                if (sha256(apk) != want) return@launch fail(a, Failure.CHECKSUM)
                val info = archiveInfo(app, apk) ?: return@launch fail(a, Failure.WRONG_APP)
                if (info.packageName != a.pkg) return@launch fail(a, Failure.WRONG_APP)
                // No certificate read is a refusal, never a pass.
                val certs = signers(info)
                if (certs.isEmpty() || certs.any { it != a.cert }) return@launch fail(a, Failure.WRONG_KEY)
                set(a.pkg) { it.copy(step = Step.INSTALLING) }
                commit(app, a, apk)
            } catch (e: Exception) {
                fail(a, Failure.REFUSED, e)
            } finally {
                apk.delete()
            }
        }
    }

    private fun fail(a: App, why: Failure, cause: Throwable? = null) {
        Log.w(TAG, "${a.pkg}: not installed ($why)", cause)
        set(a.pkg) { it.copy(step = Step.FAILED, failure = why) }
    }

    private fun download(url: String, to: File, onPercent: (Int) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            var shown = -1
            c.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != shown) { shown = percent; onPercent(percent) }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(context: Context, apk: File): PackageInfo? = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    } catch (_: Exception) {
        null
    }

    /** SHA-256 of each certificate the file is signed with, as lowercase hex. */
    private fun signers(info: PackageInfo): List<String> {
        val signing = info.signingInfo ?: return emptyList()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs.orEmpty().map { c ->
            MessageDigest.getInstance("SHA-256").digest(c.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    private fun commit(context: Context, a: App, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(a.pkg)
        // An update of an app Home itself installed goes through without a question (Android 12+).
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        // Marked as from a store, so Android 13+ does not treat the app as a downloaded file
        // and lock its restricted settings.
        if (Build.VERSION.SDK_INT >= 33) params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
        val id = installer.createSession(params)
        sessions[a.pkg] = id
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = PendingIntent.getBroadcast(
                context, id,
                Intent(context, IoriInstallReceiver::class.java).putExtra(IoriInstallReceiver.EXTRA_PKG, a.pkg),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(result.intentSender)
        }
    }

    /** The install session Android holds for each app that is with it now. */
    private val sessions = ConcurrentHashMap<String, Int>()

    /**
     * Called when a page that shows installs is in front again. Android's "install this app?"
     * question can be put away without an answer (a touch beside it, the home key), and then
     * Android says nothing at all: the app would wait for an OK for good, and so would every
     * app queued behind it. An install still unanswered a few seconds after the page is back
     * is given up, and reads as not installed.
     */
    fun resumed(context: Context) {
        val app = context.applicationContext
        _states.value.filterValues { it.step == Step.CONFIRM }.keys.forEach { pkg ->
            scope.launch {
                delay(3_000)
                if (_states.value[pkg]?.step != Step.CONFIRM) return@launch
                val id = sessions[pkg] ?: return@launch
                val installer = app.packageManager.packageInstaller
                val info = runCatching { installer.getSessionInfo(id) }.getOrNull()
                // No session left: Android has finished with it and its answer is on the way.
                // Progress past the copied file: the OK was given and the install is running.
                if (info == null || info.progress > COPIED) return@launch
                Log.w(TAG, "$pkg: the install question went unanswered")
                runCatching { installer.abandonSession(id) }
                sessions.remove(pkg)
                set(pkg) { if (it.step == Step.CONFIRM) it.copy(step = Step.FAILED, failure = Failure.REFUSED) else it }
            }
        }
    }

    /** A session's progress once its file is written and it waits for the person: 80%. */
    private const val COPIED = 0.81f

    /** What Android answered for an install; called by [IoriInstallReceiver]. */
    internal fun answered(context: Context, pkg: String, status: Int, message: String?) {
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) sessions.remove(pkg)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> set(pkg) { it.copy(step = Step.CONFIRM) }
            PackageInstaller.STATUS_SUCCESS -> {
                val version = installedVersion(context, pkg)
                set(pkg) { it.copy(step = Step.IDLE, installed = version, latest = null, failure = null) }
            }
            else -> {
                Log.w(TAG, "$pkg: Android did not install it (status $status, $message)")
                set(pkg) { it.copy(step = Step.FAILED, failure = Failure.REFUSED) }
            }
        }
    }
}

/** Hears back from Android about an install Home asked for. */
class IoriInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        IoriApps.answered(context, pkg, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android's own "install this app?" question; the answer comes back here again.
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    companion object {
        const val EXTRA_PKG = "com.wanderwildwood.iori.pkg"
    }
}
