package com.wanderwildwood.iori

import com.github.gezimos.inkos.R
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.wanderwildwood.iori.IoriAppsManifest.Entry
import com.wanderwildwood.iori.IoriAppsManifest.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The work behind "Your apps" ([IoriYourApps]), and Home's only way of installing an app: the
 * list of the house's apps from wanderthe.dev (kept in Home's files so the page shows something
 * with no connection), what is on the phone, and installing or updating an app. The first run's
 * tick boxes ([installAll]), the hand-off offer and the lines list (`install(context, pkg)`) come
 * here too, so every app Home gets passes the same checks.
 *
 * An app is downloaded only from a GitHub release of wanderwildwood's own (the list may name no
 * other address, [IoriAppsManifest.isReleaseDownload]), with a User-Agent of "Home" and nothing
 * about the phone, to Home's cache (no storage permission), and handed to Android only when
 * its size and SHA-256 are the ones in the list, it is the package and version the list names,
 * it is signed with the list's certificate and no other, that certificate is the one Home itself
 * carries for the app (assets/iori_apps.json and [IoriAccount.CALLERS]; an app Home carries no
 * key for is not shown at all), and, for an update, the app on the phone is signed with the same
 * one, and it is not older than the release out when this Home was built ([IoriApps.App.minVersionCode]).
 * The file is deleted afterwards, whatever happened; one left by a download that Android
 * stopped (Home's process killed) is cleared the next time the list is read or an app is got.
 *
 * Android 12 and later let an update of an app Home installed go through without a question
 * (USER_ACTION_NOT_REQUIRED); a first install, or an app another installer put there, asks for
 * one tap.
 */
object IoriAppInstaller {

    private const val TAG = "IoriYourApps"
    private const val CACHE_FILE = "your-apps.json"
    private const val DOWNLOADS = "your-apps"

    /** All Home says of itself to wanderthe.dev and GitHub. */
    private const val USER_AGENT = "Home"

    /** A download's progress is told in steps this big, so an e-ink row redraws ten times, not a hundred. */
    private const val PERCENT_STEP = 10

    /** Where the list stands. */
    sealed class ListState {
        object Loading : ListState()
        /** The apps, and whether they are from the copy kept on the phone because the fetch failed. */
        data class Ready(val apps: List<Entry>, val fromCopy: Boolean, val problem: String?) : ListState()
        data class Failed(val why: String) : ListState()
    }

    enum class Step { IDLE, DOWNLOADING, CHECKING, INSTALLING, NEEDS_TAP, INSTALLED, FAILED }

    /**
     * One app's install: what is happening, and, when it failed, why in words ([why], for the
     * Your apps page) and in a few words ([short], a string, for the rows of the first run, the
     * hand-off offer and the lines list).
     */
    data class Job(val step: Step = Step.IDLE, val percent: Int = 0, val why: String? = null, val short: Int? = null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchLock = Any()
    private val fetching = MutableStateFlow(false)

    private val _list = MutableStateFlow<ListState>(ListState.Loading)
    val list: StateFlow<ListState> = _list

    private val _jobs = MutableStateFlow<Map<String, Job>>(emptyMap())
    val jobs: StateFlow<Map<String, Job>> = _jobs

    private fun set(pkg: String, job: Job) = _jobs.update { it + (pkg to job) }

    // --- The list ---------------------------------------------------------------------------

    /** Shows the kept copy at once, then fetches the list again. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        synchronized(fetchLock) {
            if (fetching.value) return
            fetching.value = true
        }
        scope.launch {
            try {
                sweep(app)
                val pins = pinned(app)
                val kept = runCatching { File(app.filesDir, CACHE_FILE).readText() }.getOrNull()
                    ?.let { IoriAppsManifest.parse(it) }?.let { IoriAppsManifest.pinnedOnly(it, pins) }
                if (kept != null && _list.value !is ListState.Ready) _list.value = ListState.Ready(kept, fromCopy = true, problem = null)
                val fresh = try {
                    fetchText(IoriAppsManifest.URL, 2L * 1024 * 1024)
                } catch (e: Exception) {
                    Log.w(TAG, "the list could not be fetched", e)
                    // Reached, but no list handed over (404, 500, a page of the host's own) is not
                    // "no connection"; only a failure to connect or read is.
                    val why = app.getString(if (e is Unanswered) R.string.iori_your_apps_list_missing else R.string.iori_your_apps_list_offline)
                    _list.value = if (kept != null) ListState.Ready(kept, fromCopy = true, problem = why) else ListState.Failed(why)
                    return@launch
                }
                val parsed = IoriAppsManifest.parse(fresh)?.let { IoriAppsManifest.pinnedOnly(it, pins) }
                if (parsed == null) {
                    val why = app.getString(R.string.iori_your_apps_list_unreadable)
                    _list.value = if (kept != null) ListState.Ready(kept, fromCopy = true, problem = why) else ListState.Failed(why)
                    return@launch
                }
                runCatching {
                    val tmp = File(app.filesDir, "$CACHE_FILE.new")
                    tmp.writeText(fresh)
                    tmp.renameTo(File(app.filesDir, CACHE_FILE))
                }
                _list.value = ListState.Ready(parsed, fromCopy = false, problem = null)
            } finally {
                fetching.value = false
            }
        }
    }

    /**
     * The list as it stands once a fetch has ended (fetched now, or the kept copy when that
     * failed); null when there is none at all.
     */
    private suspend fun freshList(context: Context): List<Entry>? {
        refresh(context)
        fetching.first { !it }
        return (_list.value as? ListState.Ready)?.apps
    }

    /** The server was reached but did not hand over what was asked for. */
    private class Unanswered(why: String) : IOException(why)

    private fun fetchText(url: String, max: Long): String {
        val c = open(url)
        try {
            c.useCaches = false
            if (c.responseCode != 200) throw Unanswered("HTTP ${c.responseCode}")
            if (!IoriAppsManifest.isHttps(c.url.toString())) throw Unanswered("left https")
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > max) throw Unanswered("too long")
                }
                out.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    /** An https connection; anything else is refused before it is opened. */
    private fun open(url: String): HttpURLConnection {
        if (!IoriAppsManifest.isHttps(url)) throw IOException("not https")
        val c = URL(url).openConnection() as? javax.net.ssl.HttpsURLConnection ?: throw IOException("not https")
        // Java follows a redirect only to the same scheme, so an https address never ends on http.
        c.instanceFollowRedirects = true
        c.connectTimeout = 20_000
        c.readTimeout = 30_000
        // Android's default names the phone's model, Android version and build; this names only Home.
        c.setRequestProperty("User-Agent", USER_AGENT)
        return c
    }

    // --- The phone ----------------------------------------------------------------------------

    fun installed(context: Context, pkg: String): IoriAppsManifest.Installed? = try {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        IoriAppsManifest.Installed(info.versionName, code(info))
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    /** Whether the person has let Home install apps ("Install unknown apps"). */
    fun allowed(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Android's own screen with Home's "Allow from this source" switch. */
    fun askToAllow(context: Context) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "the install-apps screen could not be opened", it) }
    }

    /**
     * The certificates Home carries for its apps, by package: a list from the network cannot change
     * these. The same pins the account uses ([IoriAccount.CALLERS]) and Home's own list's
     * (assets/iori_apps.json); `IoriAccountTest` holds the two to the same values, and a package
     * they disagree on is left out, so it is never installed.
     */
    private fun pinned(context: Context): Map<String, String> {
        val pins = HashMap<String, String>()
        val clash = HashSet<String>()
        val all = IoriAccount.CALLERS.map { (pkg, cert) -> pkg to cert } +
            IoriApps.apps(context).map { it.pkg to it.cert }
        all.forEach { (pkg, raw) ->
            val cert = raw.lowercase()
            val had = pins.putIfAbsent(pkg, cert)
            if (had != null && had != cert) clash += pkg
        }
        clash.forEach { Log.w(TAG, "$it: two different pinned certificates, left out") }
        return pins - clash
    }

    /**
     * Deletes every downloaded file in the cache whose app has no install running. After Home's
     * process was killed mid-download nothing is running, so everything there is left over.
     */
    private fun sweep(context: Context) {
        File(context.cacheDir, DOWNLOADS).listFiles()?.forEach { file ->
            if (!busy(_jobs.value[file.name.removeSuffix(".apk")]?.step)) file.delete()
        }
    }

    // --- Installing ---------------------------------------------------------------------------

    private fun busy(step: Step?) = step == Step.DOWNLOADING || step == Step.CHECKING || step == Step.INSTALLING

    /** Downloads [entry], checks it and hands it to Android. Progress and the outcome are in [jobs]. */
    fun install(context: Context, entry: Entry) {
        val app = context.applicationContext
        if (busy(_jobs.value[entry.pkg]?.step)) return
        begin(app, entry)
    }

    /**
     * Installs or updates the app [pkg] as the list on wanderthe.dev has it, reading the list
     * first when it is not in hand. An app the list does not have (or whose certificate is not
     * the one Home pins) is not installed, and its job says so.
     */
    fun install(context: Context, pkg: String) {
        val app = context.applicationContext
        if (busy(_jobs.value[pkg]?.step)) return
        val listed = (_list.value as? ListState.Ready)?.apps?.firstOrNull { it.pkg == pkg }
        if (listed != null) return begin(app, listed)
        // Reading the list counts as the start of the download.
        set(pkg, Job(Step.DOWNLOADING))
        scope.launch {
            val entry = freshList(app)?.firstOrNull { it.pkg == pkg }
            if (entry != null) begin(app, entry)
            else set(pkg, Job(Step.FAILED, why = app.getString(R.string.iori_your_apps_why_not_listed), short = R.string.iori_apps_failed_not_listed))
        }
    }

    private fun begin(app: Context, entry: Entry) {
        if (!allowed(app)) {
            set(entry.pkg, Job(Step.FAILED, why = app.getString(R.string.iori_your_apps_why_allow), short = R.string.iori_apps_failed_allow))
            askToAllow(app)
            return
        }
        // An older release than the one out when Home was built is not even downloaded.
        if (entry.versionCode < floor(app, entry.pkg)) {
            Log.w(TAG, "${entry.pkg}: refused (${Verdict.TOO_OLD}, before download)")
            return fail(app, entry, why(Verdict.TOO_OLD))
        }
        set(entry.pkg, Job(Step.DOWNLOADING))
        scope.launch {
            sweep(app)
            val dir = File(app.cacheDir, DOWNLOADS).apply { mkdirs() }
            val apk = File(dir, "${entry.pkg}.apk")
            try {
                try {
                    download(entry, apk) { percent -> set(entry.pkg, Job(Step.DOWNLOADING, percent)) }
                } catch (e: Exception) {
                    Log.w(TAG, "${entry.pkg}: download failed", e)
                    return@launch fail(app, entry, R.string.iori_your_apps_why_network)
                }
                set(entry.pkg, Job(Step.CHECKING))
                val verdict = IoriAppsManifest.verify(entry, facts(app, entry, apk))
                if (verdict != Verdict.OK) {
                    Log.w(TAG, "${entry.pkg}: refused ($verdict)")
                    return@launch fail(app, entry, why(verdict))
                }
                set(entry.pkg, Job(Step.INSTALLING))
                commit(app, entry, apk)
            } catch (e: Exception) {
                Log.w(TAG, "${entry.pkg}: not installed", e)
                fail(app, entry, R.string.iori_your_apps_why_android, e.message)
            } finally {
                // Android has its own copy once the session is written; ours goes either way.
                apk.delete()
            }
        }
    }

    private fun fail(context: Context, entry: Entry, why: Int, detail: String? = null) {
        val words = if (detail.isNullOrBlank()) context.getString(why) else context.getString(why) + " (" + detail + ")"
        set(entry.pkg, Job(Step.FAILED, why = words, short = short(why)))
    }

    /** The few words a small row says for a failure, from the reason [IoriYourApps] gives in full. */
    private fun short(why: Int): Int = when (why) {
        R.string.iori_your_apps_why_allow -> R.string.iori_apps_failed_allow
        R.string.iori_your_apps_why_network -> R.string.iori_apps_failed_network
        R.string.iori_your_apps_why_damaged -> R.string.iori_apps_failed_checksum
        R.string.iori_your_apps_why_not_app, R.string.iori_your_apps_why_wrong_app -> R.string.iori_apps_failed_app
        R.string.iori_your_apps_why_key, R.string.iori_your_apps_why_key_changed -> R.string.iori_apps_failed_key
        R.string.iori_your_apps_why_not_listed -> R.string.iori_apps_failed_not_listed
        else -> R.string.iori_apps_failed_refused
    }

    // --- Several, one after the other (the first run) ------------------------------------------

    private val queueLock = Any()
    private var worker: kotlinx.coroutines.Job? = null
    private val _queue = MutableStateFlow<List<String>>(emptyList())

    /** The packages still to be got by [installAll]; the first is the one in hand. */
    val queue: StateFlow<List<String>> = _queue

    /**
     * Gets several apps one after the other through [install], so Android's "install this app?"
     * questions come one at a time. An app that fails is passed over and the rest go on, except
     * when Home may not install apps at all: then the same answer waits for every other app, and
     * the queue stops.
     */
    fun installAll(context: Context, pkgs: List<String>) {
        val app = context.applicationContext
        synchronized(queueLock) {
            _queue.update { now -> now + pkgs.filter { it !in now } }
            if (worker != null) return
            worker = scope.launch {
                while (true) {
                    val pkg = synchronized(queueLock) {
                        _queue.value.firstOrNull().also { if (it == null) worker = null }
                    } ?: break
                    // install() has set the job before it returns (or left a running one alone).
                    install(app, pkg)
                    val end = jobs.first { all -> all[pkg]?.step.let { it == Step.INSTALLED || it == Step.FAILED || it == null || it == Step.IDLE } }[pkg]
                    if (end?.step == Step.FAILED && !allowed(app)) _queue.value = listOf(pkg)
                    _queue.update { it - pkg }
                }
            }
        }
    }

    /** Lets the app in hand finish and drops the ones waiting behind it. */
    fun stopQueue() {
        _queue.update { it.take(1) }
    }

    private fun why(verdict: Verdict): Int = when (verdict) {
        Verdict.WRONG_SIZE, Verdict.WRONG_CHECKSUM -> R.string.iori_your_apps_why_damaged
        Verdict.NOT_AN_APP -> R.string.iori_your_apps_why_not_app
        Verdict.WRONG_APP, Verdict.WRONG_VERSION -> R.string.iori_your_apps_why_wrong_app
        Verdict.WRONG_KEY, Verdict.NOT_PINNED -> R.string.iori_your_apps_why_key
        Verdict.KEY_CHANGED -> R.string.iori_your_apps_why_key_changed
        Verdict.NOT_NEWER -> R.string.iori_your_apps_why_not_newer
        Verdict.TOO_OLD -> R.string.iori_your_apps_why_old_release
        Verdict.OK -> R.string.iori_your_apps_why_android
    }

    private fun download(entry: Entry, to: File, onPercent: (Int) -> Unit) {
        val limit = if (entry.size > 0) entry.size else IoriAppsManifest.MAX_SIZE
        val c = open(entry.apk)
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            // GitHub hands a release download to its own file hosts; anywhere else is not followed.
            if (!IoriAppsManifest.isDownloadHost(c.url.toString())) throw IOException("left GitHub")
            val total = if (entry.size > 0) entry.size else c.contentLengthLong
            var done = 0L
            var shown = -1
            c.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        done += n
                        // Longer than the list says: stop here, the file is not the one listed.
                        if (done > limit) throw IOException("longer than $limit bytes")
                        out.write(buffer, 0, n)
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt() / PERCENT_STEP * PERCENT_STEP
                            if (percent != shown) { shown = percent; onPercent(percent) }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    private fun facts(context: Context, entry: Entry, apk: File): IoriAppsManifest.Facts {
        val archive = archiveInfo(context, apk)
        val installed = installedInfo(context, entry.pkg)
        return IoriAppsManifest.Facts(
            size = apk.length(),
            sha256 = sha256(apk),
            pkg = archive?.packageName,
            versionCode = archive?.let(::code) ?: -1L,
            signers = archive?.let(::signers).orEmpty(),
            installedSigners = installed?.let(::signers),
            installedVersionCode = installed?.let(::code),
            pinned = pinned(context)[entry.pkg],
            floor = floor(context, entry.pkg),
        )
    }

    /** The oldest release of [pkg] Home installs: the one out when this Home was built (assets/iori_apps.json). */
    private fun floor(context: Context, pkg: String): Long = IoriApps.find(context, pkg)?.minVersionCode ?: 0L

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
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

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

    @Suppress("DEPRECATION")
    private fun installedInfo(context: Context, pkg: String): PackageInfo? = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * SHA-256 of each certificate a package is signed with, as lowercase hex. With key rotation
     * the history holds the older keys too, so a rotated key never passes as "only this one".
     */
    private fun signers(info: PackageInfo): List<String> {
        val signing = info.signingInfo ?: return emptyList()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs.orEmpty().map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }
    }

    private fun commit(context: Context, entry: Entry, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(entry.pkg)
        params.setSize(apk.length())
        // An update of an app Home itself installed goes through with no question (Android 12+).
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        // Marked as from a store, so Android 13+ does not lock the app's restricted settings.
        if (Build.VERSION.SDK_INT >= 33) params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
        val id = installer.createSession(params)
        sessions[entry.pkg] = id
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val result = PendingIntent.getBroadcast(
                    context, id,
                    Intent(context, IoriInstallReceiver::class.java)
                        .putExtra(IoriInstallReceiver.EXTRA_PKG, entry.pkg),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(result.intentSender)
            }
        } catch (e: Exception) {
            sessions.remove(entry.pkg)
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    /** Android's answer to an install; called by [IoriInstallReceiver]. */
    internal fun answered(context: Context, pkg: String, status: Int, message: String?) {
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) sessions.remove(pkg)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> set(pkg, Job(Step.NEEDS_TAP))
            PackageInstaller.STATUS_SUCCESS -> set(pkg, Job(Step.INSTALLED))
            else -> {
                Log.w(TAG, "$pkg: Android did not install it (status $status, $message)")
                val why = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.iori_your_apps_why_aborted
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> R.string.iori_your_apps_why_blocked
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> R.string.iori_your_apps_why_conflict
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> R.string.iori_your_apps_why_incompatible
                    PackageInstaller.STATUS_FAILURE_INVALID -> R.string.iori_your_apps_why_not_app
                    PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.iori_your_apps_why_storage
                    else -> R.string.iori_your_apps_why_android
                }
                set(pkg, Job(Step.FAILED, why = context.getString(why), short = short(why)))
            }
        }
    }

    /** The install session Android holds for each app that is with it now. */
    private val sessions = ConcurrentHashMap<String, Int>()

    /** A session's progress once its file is written and it waits for the person: 80%. */
    private const val COPIED = 0.81f

    /**
     * Called when the page is in front again. Android's "install this app?" question can be put
     * away without an answer, and then Android says nothing at all. An install still waiting a
     * few seconds after the page is back is given up, and its row says so.
     */
    fun resumed(context: Context) {
        val app = context.applicationContext
        _jobs.value.filterValues { it.step == Step.NEEDS_TAP }.keys.forEach { pkg ->
            scope.launch {
                delay(3_000)
                if (_jobs.value[pkg]?.step != Step.NEEDS_TAP) return@launch
                val id = sessions[pkg] ?: return@launch
                val installer = app.packageManager.packageInstaller
                val info = runCatching { installer.getSessionInfo(id) }.getOrNull()
                // No session: Android's answer is on its way. Past the copied file: the tap was given.
                if (info == null || info.progress > COPIED) return@launch
                runCatching { installer.abandonSession(id) }
                sessions.remove(pkg)
                _jobs.update { all ->
                    if (all[pkg]?.step != Step.NEEDS_TAP) all
                    else all + (pkg to Job(Step.FAILED, why = app.getString(R.string.iori_your_apps_why_unanswered), short = R.string.iori_apps_failed_refused))
                }
            }
        }
    }

    /** A finished outcome (installed, failed) stays on its row until the page is opened again. */
    fun clearFinished() {
        _jobs.update { all -> all.filterValues { busy(it.step) || it.step == Step.NEEDS_TAP } }
    }
}

/** Hears back from Android about an install Home asked for ([IoriAppInstaller]). */
class IoriInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        IoriAppInstaller.answered(context, pkg, status, message)
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
