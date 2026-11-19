package com.wanderwildwood.iori

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.gezimos.inkos.BuildConfig
import com.google.gson.JsonParser
import com.wanderwildwood.iori.IoriAccount.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * The rules of signing in to a Nextcloud, apart from the phone so they can be tested: what a
 * typed address becomes, which addresses Home will talk to, and what the server's two answers
 * of Login Flow v2 mean.
 */
object IoriLogin {

    /** How Home names itself to the server; Nextcloud lists the app password under this name. */
    const val AGENT = "Home (iori)"

    class Started(val loginUrl: String, val pollUrl: String, val token: String)

    /** What was typed, as an address: "cloud.example.org" becomes "https://cloud.example.org". Null when it cannot be one. */
    fun normalise(typed: String): String? {
        val trimmed = typed.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val whole = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
        val address = whole.trimEnd('/')
        val host = try {
            URI(address).host
        } catch (_: Exception) {
            null
        }
        return if (host.isNullOrEmpty()) null else address
    }

    /**
     * Whether Home will send a login to [address]: https only. A debug build ([debugBuild]) also
     * takes plain http to a host on the phone's own network, which is how it is tried against a
     * stand-in server; a release never sends the account in the clear.
     */
    fun usable(address: String, debugBuild: Boolean): Boolean {
        if (address.startsWith("https://", true)) return true
        if (!debugBuild || !address.startsWith("http://", true)) return false
        val host = try {
            URI(address).host
        } catch (_: Exception) {
            null
        } ?: return false
        return host.equals("localhost", true) || nearby(host)
    }

    private val dottedQuad = Regex("[0-9]{1,3}([.][0-9]{1,3}){3}")
    private val hexGroup = Regex("[0-9A-Fa-f]{1,4}")

    /**
     * Whether [bare] is a whole numeric address: four numbers up to 255, or eight groups of hex
     * (fewer with one "::"). Only such a string reaches InetAddress, which would look up anything else.
     */
    private fun literal(bare: String): Boolean {
        if (dottedQuad.matches(bare)) return bare.split('.').all { it.toInt() <= 255 }
        if (!bare.contains(':')) return false
        val halves = bare.split("::")
        if (halves.size > 2) return false
        val groups = halves.flatMap { if (it.isEmpty()) emptyList() else it.split(':') }
        if (!groups.all { hexGroup.matches(it) }) return false
        return if (halves.size == 2) groups.size < 8 else groups.size == 8
    }

    /**
     * Whether [host] is written as a number (IPv4, or IPv6 in brackets) for this phone or its own
     * network: 127/8, ::1, 10/8, 172.16/12, 192.168/16 and the like. A name is never looked up,
     * so "10.example.org" is a name and not a nearby host.
     */
    private fun nearby(host: String): Boolean {
        val bare = host.removePrefix("[").removeSuffix("]")
        if (!literal(bare)) return false
        // A whole numeric address is read as it is written; InetAddress asks no one for it.
        val inet = try {
            InetAddress.getByName(bare)
        } catch (_: Exception) {
            null
        } ?: return false
        return inet.isLoopbackAddress || inet.isSiteLocalAddress
    }

    /** The server's answer to `POST /index.php/login/v2`: where to sign in and where to ask afterwards. */
    fun parseStart(body: String, debugBuild: Boolean): Started? = try {
        val o = JsonParser.parseString(body).asJsonObject
        val poll = o.getAsJsonObject("poll")
        val started = Started(o.get("login").asString, poll.get("endpoint").asString, poll.get("token").asString)
        if (started.token.isEmpty() || !usable(started.loginUrl, debugBuild) || !usable(started.pollUrl, debugBuild)) null else started
    } catch (_: Exception) {
        null
    }

    /**
     * The server's answer once the person has signed in: the account. The server names itself in
     * it; where that name is one Home would not talk to, the address that was asked ([asked]) is kept.
     */
    fun parsePoll(body: String, asked: String, debugBuild: Boolean): Account? = try {
        val o = JsonParser.parseString(body).asJsonObject
        val named = o.get("server")?.asString?.trimEnd('/').orEmpty()
        val user = o.get("loginName").asString
        val password = o.get("appPassword").asString
        if (user.isEmpty() || password.isEmpty()) null
        else Account(if (named.isNotEmpty() && usable(named, debugBuild)) named else asked.trimEnd('/'), user, password)
    } catch (_: Exception) {
        null
    }

    /**
     * The server's answer to `GET /ocs/v2.php/core/getapppassword`: the app password it made in
     * place of a login password. Null when the answer holds none.
     */
    fun parseAppPassword(body: String): String? = try {
        JsonParser.parseString(body).asJsonObject
            .getAsJsonObject("ocs").getAsJsonObject("data").get("apppassword").asString
            .takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    /**
     * Whether the server's 403 to `core/getapppassword` says what was typed already is an app
     * password. That address asks for a confirmed password first, and only a login with the
     * login password counts as confirmed, so an app password is turned away there with "Password
     * confirmation is required" (seen on Nextcloud 34). Where a login cannot be confirmed by
     * design, the address itself answers "You cannot request an new apppassword with an
     * apppassword". Nextcloud also answers 403 with no words when it cannot read a login password
     * back (single sign-on, some external logins), and a proxy may block the address; neither of
     * those says this.
     */
    fun isAppPasswordRefusal(body: String): Boolean = try {
        val message = JsonParser.parseString(body).asJsonObject.getAsJsonObject("ocs").getAsJsonObject("meta")
            .get("message").asString
        message.contains("apppassword with an apppassword", ignoreCase = true) ||
            message.equals("Password confirmation is required", ignoreCase = true)
    } catch (_: Exception) {
        false
    }

    /** The host of [server], as shown beside the user's name. */
    fun host(server: String): String = try {
        URI(server).let { u -> (u.host ?: server) + if (u.port > 0) ":${u.port}" else "" }
    } catch (_: Exception) {
        server
    }

    /** Whether the package Android would open a web address with is something to browse with. */
    fun isBrowser(packageName: String?, own: String): Boolean =
        packageName != null && packageName != own && !packageName.contains("webview_shell")
}

/**
 * Signing in and out of the phone's Nextcloud account, for the first-run page and the Settings
 * row ([IoriNextcloudUi]). The waiting is done here and not in a screen, so it goes on while
 * the person is away in the browser, whatever screen of Home they come back to.
 */
object IoriNextcloud {

    enum class Problem { ADDRESS, NEEDS_HTTPS, UNREACHABLE, NOT_NEXTCLOUD, WRONG_PASSWORD, NO_BROWSER, NOT_KEPT, GAVE_UP, NO_APP_PASSWORD }

    sealed class Phase {
        object Idle : Phase()
        object Asking : Phase()
        object Waiting : Phase()
        object Checking : Phase()
        class Failed(val problem: Problem) : Phase()
    }

    /** Who is signed in, as shown: the user's name and the server's host. */
    class Shown(val user: String, val host: String)

    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set

    var shown by mutableStateOf<Shown?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Held while an account is stored and while a try is cancelled, so a cancel never lands in between. */
    private val storing = Any()

    private fun cancelTry() = synchronized(storing) { job?.cancel() }

    /** Reads who is signed in; called when a screen that shows it opens. */
    fun load(context: Context) {
        shown = IoriAccount.account(context)?.let { Shown(it.user, IoriLogin.host(it.server)) }
    }

    private suspend fun set(next: Phase) = withContext(Dispatchers.Main) { phase = next }

    /**
     * Keeps [account], unless this try was stopped or a newer one began while the server was
     * asked: then nothing is kept. An app password made for this try ([made]) and not kept is
     * given back to the server, so it does not linger in the device list.
     */
    private suspend fun keep(context: Context, account: Account, made: Boolean) {
        val mine = currentCoroutineContext()[Job]
        // Checked and stored under the lock cancelTry() takes, so a stop cannot fall in between.
        val kept = synchronized(storing) {
            if (mine?.isActive == false) null else IoriAccount.signIn(context, account)
        }
        if (kept != true && made) giveBack(account)
        if (kept == null) return
        // Shown even when the try is stopped right after the store; the phase only while it is ours.
        withContext(NonCancellable + Dispatchers.Main) {
            load(context)
            if (mine?.isActive != false) phase = if (kept) Phase.Idle else Phase.Failed(Problem.NOT_KEPT)
        }
    }

    private fun giveBack(account: Account) {
        scope.launch { call("DELETE", "${account.server}/ocs/v2.php/core/apppassword", account = account) }
    }

    private fun address(typed: String): Pair<String?, Problem?> {
        val address = IoriLogin.normalise(typed) ?: return null to Problem.ADDRESS
        if (!IoriLogin.usable(address, BuildConfig.DEBUG)) return null to Problem.NEEDS_HTTPS
        return address to null
    }

    /** Whether a browser is there to open the server's sign-in page with. */
    fun hasBrowser(context: Context): Boolean {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/")).addCategory(Intent.CATEGORY_BROWSABLE)
        val answer = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }.getOrNull()
        if (answer != "android") return IoriLogin.isBrowser(answer, context.packageName)
        return runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrDefault(emptyList())
            .any { IoriLogin.isBrowser(it.activityInfo?.packageName, context.packageName) }
    }

    /**
     * Login Flow v2: asks the server for its sign-in page, opens it in the browser, and asks every
     * two seconds, for the twenty minutes the server allows, whether the person has finished.
     */
    fun signInWithBrowser(context: Context, typed: String) {
        val app = context.applicationContext
        val (address, problem) = address(typed)
        if (address == null) {
            phase = Phase.Failed(problem!!)
            return
        }
        if (!hasBrowser(app)) {
            phase = Phase.Failed(Problem.NO_BROWSER)
            return
        }
        cancelTry()
        phase = Phase.Asking
        job = scope.launch {
            val reply = call("POST", "$address/index.php/login/v2", form = "")
            val started = reply?.takeIf { it.code in 200..299 }?.let { IoriLogin.parseStart(it.body, BuildConfig.DEBUG) }
            if (started == null) {
                set(Phase.Failed(if (reply == null) Problem.UNREACHABLE else Problem.NOT_NEXTCLOUD))
                return@launch
            }
            val opened = runCatching {
                app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(started.loginUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (!opened) {
                set(Phase.Failed(Problem.NO_BROWSER))
                return@launch
            }
            set(Phase.Waiting)
            // Twenty minutes by the clock from here, however long each answer takes to come.
            val until = SystemClock.elapsedRealtime() + POLL_FOR_MS
            while (true) {
                delay(2000)
                if (SystemClock.elapsedRealtime() >= until) break
                val answer = call("POST", started.pollUrl, form = "token=" + URLEncoder.encode(started.token, "UTF-8"))
                val account = answer?.takeIf { it.code in 200..299 }?.let { IoriLogin.parsePoll(it.body, address, BuildConfig.DEBUG) }
                if (account != null) {
                    keep(app, account, made = true)
                    return@launch
                }
            }
            set(Phase.Failed(Problem.GAVE_UP))
        }
    }

    /**
     * A password typed in here, checked with the server. Only an app password is ever kept: the
     * server is asked to make one from what was typed, and when it says no because what was typed
     * already is one (a 403 that says so), that is kept as it is. A login password is never kept.
     */
    fun signInTyped(context: Context, typed: String, user: String, password: String) {
        val app = context.applicationContext
        val (address, problem) = address(typed)
        if (address == null) {
            phase = Phase.Failed(problem!!)
            return
        }
        cancelTry()
        phase = Phase.Checking
        job = scope.launch {
            val typedAccount = Account(address, user.trim(), password.trim())
            val reply = call("GET", "$address/ocs/v2.php/cloud/user?format=json", account = typedAccount)
            when {
                reply == null -> set(Phase.Failed(Problem.UNREACHABLE))
                reply.code == 401 || reply.code == 403 -> set(Phase.Failed(Problem.WRONG_PASSWORD))
                reply.code !in 200..299 -> set(Phase.Failed(Problem.NOT_NEXTCLOUD))
                else -> {
                    val swap = call("GET", "$address/ocs/v2.php/core/getapppassword?format=json", account = typedAccount)
                    val appPassword = swap?.takeIf { it.code in 200..299 }?.let { IoriLogin.parseAppPassword(it.body) }
                    when {
                        swap == null -> set(Phase.Failed(Problem.UNREACHABLE))
                        appPassword != null -> keep(app, typedAccount.copy(password = appPassword), made = true)
                        // The server makes no app password from an app password: what was typed is one.
                        swap.code == 403 && IoriLogin.isAppPasswordRefusal(swap.body) -> keep(app, typedAccount, made = false)
                        swap.code == 401 -> set(Phase.Failed(Problem.WRONG_PASSWORD))
                        else -> set(Phase.Failed(Problem.NO_APP_PASSWORD))
                    }
                }
            }
        }
    }

    /** Stops asking or waiting, and clears what was said about the last try. */
    fun stop() {
        cancelTry()
        job = null
        phase = Phase.Idle
    }

    /**
     * Forgets the account and tells the apps, then asks the server to delete the app password,
     * so the "Home" entry leaves its device list. The server is asked once; if it cannot be
     * reached the entry stays there until it is removed on the server.
     */
    fun signOut(context: Context) {
        val app = context.applicationContext
        stop()
        val was = IoriAccount.signOut(app)
        load(app)
        if (was != null) scope.launch { call("DELETE", "${was.server}/ocs/v2.php/core/apppassword", account = was) }
    }

    /** How long Login Flow v2 is asked about: the twenty minutes the server keeps it open. */
    private const val POLL_FOR_MS = 20 * 60 * 1000L

    private class Reply(val code: Int, val body: String)

    /** One request; null when the server could not be reached at all. */
    private fun call(method: String, url: String, form: String? = null, account: Account? = null): Reply? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", IoriLogin.AGENT)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("OCS-APIRequest", "true")
                if (account != null) {
                    val pair = "${account.user}:${account.password}".toByteArray(Charsets.UTF_8)
                    setRequestProperty("Authorization", "Basic " + Base64.encodeToString(pair, Base64.NO_WRAP))
                }
                if (form != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            Reply(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
