package com.wanderwildwood.iori

import com.google.gson.JsonParser
import com.wanderwildwood.iori.IoriAccount.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IoriAccountTest {

    private val memo = "com.wanderwildwood.oboegaki"
    private val recipes = "com.wanderwildwood.kondate"
    private val memoCert = IoriAccount.CALLERS.getValue(memo)
    private val debugCert = IoriAccount.DEBUG_CERTS.first()
    private val strange = "00".repeat(32)

    private fun allowed(debug: Boolean, vararg signed: Pair<String, List<String>?>): Boolean {
        val by = signed.toMap()
        return IoriAccount.allowed(signed.map { it.first }, debug) { by[it] }
    }

    // ---- who is handed the account -------------------------------------------------------------

    @Test
    fun thePinnedCertsAreTheOnesTheAppListInstallsBy() {
        val apps = JsonParser.parseString(File("src/iori/assets/iori_apps.json").readText()).asJsonObject.getAsJsonArray("apps")
        val listed = apps.associate { it.asJsonObject.get("package").asString to it.asJsonObject.get("cert").asString.lowercase() }
        assertTrue(IoriAccount.CALLERS.isNotEmpty())
        IoriAccount.CALLERS.forEach { (pkg, cert) ->
            assertEquals("cert pinned for $pkg", listed[pkg], cert)
            assertEquals(64, cert.length)
        }
    }

    @Test
    fun aPinnedAppSignedWithItsReleaseCertIsAnswered() {
        assertTrue(allowed(false, memo to listOf(memoCert)))
        assertTrue(allowed(false, recipes to listOf(IoriAccount.CALLERS.getValue(recipes))))
    }

    @Test
    fun theSameNameUnderAnotherCertGetsNothing() {
        assertFalse(allowed(false, memo to listOf(strange)))
        // One app's cert does not open the door for another.
        assertFalse(allowed(false, memo to listOf(IoriAccount.CALLERS.getValue(recipes))))
        // Every signer has to be the pinned one.
        assertFalse(allowed(false, memo to listOf(memoCert, strange)))
    }

    @Test
    fun noCertificateReadIsARefusal() {
        assertFalse(allowed(false, memo to null))
        assertFalse(allowed(false, memo to emptyList()))
        assertFalse(IoriAccount.allowed(emptyList(), false) { listOf(memoCert) })
    }

    @Test
    fun anAppThatIsNotOnTheListGetsNothingWhateverItIsSignedWith() {
        assertFalse(allowed(false, "com.wanderwildwood.tokei" to listOf(memoCert)))
        assertFalse(allowed(false, "org.example.notes" to listOf(memoCert)))
        assertFalse(allowed(false, "$memo.debug" to listOf(memoCert)))
        assertFalse(allowed(true, "org.example.notes" to listOf(debugCert)))
    }

    @Test
    fun aDebugCertPassesOnlyInADebugBuildOfHome() {
        assertTrue(allowed(true, memo to listOf(debugCert)))
        assertFalse(allowed(false, memo to listOf(debugCert)))
        assertFalse(allowed(true, memo to listOf(debugCert, strange)))
        // A debug build of Home still answers the release.
        assertTrue(allowed(true, memo to listOf(memoCert)))
    }

    @Test
    fun aDebugBuildOfAPinnedAppIsAnsweredOnlyByADebugHomeAndOnlyUnderTheDebugCert() {
        assertTrue(allowed(true, "$memo.debug" to listOf(debugCert)))
        // Never under the release cert, which a ".debug" build is not signed with.
        assertFalse(allowed(true, "$memo.debug" to listOf(memoCert)))
        assertFalse(allowed(true, "$memo.debug" to listOf(debugCert, strange)))
        // A release Home answers no debug build.
        assertFalse(allowed(false, "$memo.debug" to listOf(debugCert)))
        // Only the debug build of a pinned app, and no certificate read is still a refusal.
        assertFalse(allowed(true, "org.example.notes.debug" to listOf(debugCert)))
        assertFalse(allowed(true, "$memo.debug.debug" to listOf(debugCert)))
        assertFalse(allowed(true, "$memo.debug" to null))
        assertFalse(allowed(true, "$memo.debug" to emptyList()))
    }

    @Test
    fun oneGoodPackageOfASharedUidIsEnough() {
        assertTrue(allowed(false, "org.example.other" to listOf(strange), memo to listOf(memoCert)))
    }

    // ---- the account as kept: signed out, signed in, changed, signed out -------------------------

    private class Memory(var text: String? = null, var fails: Boolean = false) : IoriAccount.Backing {
        var writes = 0
        override fun read(): String? = text
        override fun write(text: String) {
            if (fails) throw java.io.IOException("full")
            this.text = text
            writes++
        }
    }

    /** Seals by reversing, so a test can see that the plain password is never what is written. */
    private class Reversing(var canSeal: Boolean = true, var canOpen: Boolean = true) : IoriAccount.Sealer {
        override fun seal(plain: String): String? = if (canSeal) "s:" + plain.reversed() else null
        override fun open(sealed: String): String? = if (canOpen && sealed.startsWith("s:")) sealed.removePrefix("s:").reversed() else null
    }

    private fun store(memory: Memory = Memory(), sealer: Reversing = Reversing()): IoriAccount.Store {
        var n = 0
        return IoriAccount.Store(memory, sealer) { "stamp-${++n}" }
    }

    private val ada = Account("https://cloud.example.org", "ada", "app-password-one")

    @Test
    fun aNewPhoneIsSignedOut() {
        val s = store()
        assertNull(s.account())
        assertEquals("", s.stamp())
        assertFalse(s.signOut())
    }

    @Test
    fun signingInKeepsTheAccountAndStampsIt() {
        val memory = Memory()
        val s = store(memory)
        assertTrue(s.signIn(ada))
        assertEquals(ada, s.account())
        assertEquals("stamp-1", s.stamp())
        assertFalse("the password is not written in the clear", memory.text!!.contains(ada.password))
    }

    @Test
    fun aChangedPasswordReplacesTheAccountUnderANewStamp() {
        val s = store()
        s.signIn(ada)
        val before = s.stamp()
        assertTrue(s.signIn(ada.copy(password = "app-password-two")))
        assertEquals("app-password-two", s.account()!!.password)
        assertNotEquals(before, s.stamp())
    }

    @Test
    fun signingOutForgetsTheAccountAndStampsAgain() {
        val memory = Memory()
        val s = store(memory)
        s.signIn(ada)
        val before = s.stamp()
        assertTrue(s.signOut())
        assertNull(s.account())
        assertNotEquals(before, s.stamp())
        // Nothing left to forget: no second change is announced.
        assertFalse(s.signOut())
        // Once the server has ended it, nothing of the account is left.
        val waiting = s.pending().single()
        assertTrue(s.revoked(waiting.id))
        assertTrue(s.pending().isEmpty())
        assertFalse(memory.text!!.contains("ada"))
        assertFalse(memory.text!!.contains("cloud.example.org"))
    }

    @Test
    fun aSignOutWaitsSealedUntilTheServerHasEndedIt() {
        val memory = Memory()
        val s = store(memory)
        s.signIn(ada)
        s.signOut()
        // Not served any more, but kept to be ended on the server; never in the clear.
        assertNull(s.account())
        assertEquals(ada.copy(uid = null), s.pending().single().account)
        assertFalse(memory.text!!.contains(ada.password))
        // A new sign-in does not drop it: the old password is still live on the server.
        assertTrue(s.signIn(ada.copy(password = "app-password-two")))
        assertEquals("app-password-two", s.account()!!.password)
        assertEquals(ada.password, s.pending().single().account.password)
        // An unknown id changes nothing; the right one forgets it.
        assertFalse(s.revoked("nothing-like-it"))
        assertEquals(1, s.pending().size)
        assertTrue(s.revoked(s.pending().single().id))
        assertTrue(s.pending().isEmpty())
        assertEquals("app-password-two", s.account()!!.password)
    }

    @Test
    fun onlyTheLastFewSignOutsWait() {
        val s = store()
        repeat(IoriAccount.MAX_PENDING + 3) { i ->
            s.signIn(ada.copy(password = "p$i"))
            s.signOut()
        }
        val waiting = s.pending()
        assertEquals(IoriAccount.MAX_PENDING, waiting.size)
        assertEquals("p${IoriAccount.MAX_PENDING + 2}", waiting.last().account.password)
        assertEquals(waiting.size, waiting.map { it.id }.toSet().size)
    }

    @Test
    fun aSignOutWhosePasswordNoLongerOpensIsNotTriedAndGoesAtTheNextEnd() {
        val memory = Memory()
        store(memory).apply { signIn(ada); signOut(); signIn(ada.copy(password = "two")); signOut() }
        val sealer = Reversing()
        val s = store(memory, sealer)
        assertEquals(2, s.pending().size)
        sealer.canOpen = false
        assertTrue(s.pending().isEmpty())
        sealer.canOpen = true
        val first = s.pending().first().id
        // Ending one also drops any that no longer open (none here): exactly one goes.
        assertTrue(s.revoked(first))
        assertEquals(1, s.pending().size)
    }

    // ---- the user id -----------------------------------------------------------------------------

    @Test
    fun theUserIdIsKeptBesideTheLoginName() {
        val s = store()
        val anna = Account("https://cloud.example.org", "anna@example.org", "app-password-one", "anna")
        assertTrue(s.signIn(anna))
        assertEquals(anna, s.account())
        assertEquals("anna", s.account()!!.userId)
        assertEquals("anna@example.org", s.account()!!.user)
    }

    @Test
    fun anAccountKeptWithoutAUserIdReadsAsItsLoginNameUntilFilled() {
        val s = store()
        s.signIn(ada)
        assertNull(s.account()!!.uid)
        assertEquals("ada", s.account()!!.userId)
        val stamp = s.stamp()
        // Filled only for the account that was asked about.
        assertFalse(s.fillUid("an-older-stamp", "Ada"))
        assertTrue(s.fillUid(stamp, "Ada"))
        assertEquals("Ada", s.account()!!.userId)
        assertEquals("ada", s.account()!!.user)
        assertEquals(ada.password, s.account()!!.password)
        // A new stamp, so the apps read the row again.
        assertNotEquals(stamp, s.stamp())
        assertFalse(s.fillUid(s.stamp(), "Ada"))
        assertFalse(s.fillUid(s.stamp(), " "))
    }

    @Test
    fun noUserIdIsFilledIntoASignedOutPhone() {
        val s = store()
        s.signIn(ada)
        val stamp = s.stamp()
        s.signOut()
        assertFalse(s.fillUid(stamp, "ada"))
        assertFalse(s.fillUid(s.stamp(), "ada"))
        assertNull(s.account())
    }

    @Test
    fun theProviderRowHasTheUserIdFifth() {
        assertEquals(listOf("server", "user", "password", "stamp", "uid"), IoriAccount.COLUMNS.toList())
    }

    @Test
    fun anAccountNeverShowsItsPassword() {
        assertFalse(ada.toString().contains(ada.password))
    }

    @Test
    fun theServerIsKeptWrittenOneWay() {
        val s = store()
        s.signIn(ada.copy(server = "HTTPS://Cloud.Example.org:443/NextCloud/"))
        assertEquals("https://cloud.example.org/NextCloud", s.account()!!.server)
    }

    @Test
    fun aPasswordThatCannotBeSealedIsNotKept() {
        val memory = Memory()
        val s = store(memory, Reversing(canSeal = false))
        assertFalse(s.signIn(ada))
        assertNull(s.account())
        assertEquals(0, memory.writes)
    }

    @Test
    fun aFailedWriteLeavesTheOldAccount() {
        val memory = Memory()
        val s = store(memory)
        s.signIn(ada)
        memory.fails = true
        assertFalse(s.signIn(ada.copy(user = "tomas")))
        assertEquals(ada, s.account())
        assertFalse(s.signOut())
        assertEquals(ada, s.account())
    }

    @Test
    fun aLostKeyReadsAsSignedOut() {
        val memory = Memory()
        store(memory).signIn(ada)
        val s = store(memory, Reversing(canOpen = false))
        assertNull(s.account())
        // The stamp is still there, and signing out clears what is left.
        assertTrue(s.signOut())
    }

    @Test
    fun aDamagedFileReadsAsSignedOut() {
        assertNull(store(Memory("not json")).account())
        assertNull(store(Memory("[]")).account())
        assertNull(store(Memory("{\"server\":\"https://cloud.example.org\",\"user\":\"ada\"}")).account())
        assertNull(store(Memory("{\"server\":7,\"user\":{},\"sealed\":\"s:x\"}")).account())
    }

    @Test
    fun blanksAreNotAnAccount() {
        val s = store()
        assertFalse(s.signIn(Account("", "ada", "p")))
        assertFalse(s.signIn(Account("https://cloud.example.org", " ", "p")))
        assertFalse(s.signIn(Account("https://cloud.example.org", "ada", "")))
        assertNull(s.account())
    }

    // ---- the rules of signing in ---------------------------------------------------------------

    @Test
    fun aTypedAddressBecomesAnHttpsOne() {
        assertEquals("https://cloud.example.org", IoriLogin.normalise(" cloud.example.org/ "))
        assertEquals("https://cloud.example.org/nextcloud", IoriLogin.normalise("https://cloud.example.org/nextcloud/"))
        assertEquals("http://cloud.example.org", IoriLogin.normalise("http://cloud.example.org"))
        assertNull(IoriLogin.normalise(""))
        assertNull(IoriLogin.normalise("my cloud"))
        assertNull(IoriLogin.normalise("https://"))
    }

    @Test
    fun aReleaseTalksOnlyToHttps() {
        assertTrue(IoriLogin.usable("https://cloud.example.org", false))
        assertFalse(IoriLogin.usable("http://cloud.example.org", false))
        assertFalse(IoriLogin.usable("http://192.168.1.5", false))
        assertFalse(IoriLogin.usable("ftp://cloud.example.org", false))
        // A debug build also takes a stand-in server on its own network, and nothing further out.
        assertTrue(IoriLogin.usable("http://10.0.2.2:8085", true))
        assertFalse(IoriLogin.usable("http://cloud.example.org", true))
    }

    @Test
    fun aDebugBuildTakesPlainHttpOnlyToANumberOnItsOwnNetwork() {
        val nearby = listOf(
            "http://172.20.0.5", "http://172.16.0.1:8080", "http://192.168.1.5", "http://10.0.2.2:8085",
            "http://127.0.0.1", "http://[::1]:8080", "http://localhost:8080", "http://LOCALHOST",
        )
        nearby.forEach { assertTrue(it, IoriLogin.usable(it, true)) }
        // A name is never looked up, however it begins; a public number is not nearby.
        val far = listOf(
            "http://10.example.org", "http://192.168.example.org", "http://127.0.0.1.example.org",
            "http://8.8.8.8", "http://172.32.0.1", "http://[2001:4860:4860::8888]", "http://cloud.example.org",
            // Not whole numbers, so they would be looked up: never let through.
            "http://10.0.0.300", "http://999.1.1.1", "http://[1:2]", "http://[:::]", "http://[1::2::3]",
        )
        far.forEach { assertFalse(it, IoriLogin.usable(it, true)) }
        // A release takes none of them.
        (nearby + far).forEach { assertFalse(it, IoriLogin.usable(it, false)) }
    }

    @Test
    fun theAppPasswordIsReadFromTheServersSwap() {
        val body = """{"ocs":{"meta":{"status":"ok","statuscode":200},"data":{"apppassword":"app-password-two"}}}"""
        assertEquals("app-password-two", IoriLogin.parseAppPassword(body))
        assertNull(IoriLogin.parseAppPassword("""{"ocs":{"meta":{"statuscode":403},"data":[]}}"""))
        assertNull(IoriLogin.parseAppPassword("""{"ocs":{"data":{"apppassword":""}}}"""))
        assertNull(IoriLogin.parseAppPassword("""{"ocs":{"data":{}}}"""))
        assertNull(IoriLogin.parseAppPassword("<html>hello</html>"))
        assertNull(IoriLogin.parseAppPassword(""))
    }

    @Test
    fun onlyTheServersOwnWordsMakeA403AnAppPassword() {
        val already = """{"ocs":{"meta":{"status":"failure","statuscode":403,"message":"You cannot request an new apppassword with an apppassword"},"data":[]}}"""
        assertTrue(IoriLogin.isAppPasswordRefusal(already))
        // Nextcloud 34's real answer to an app password: its password check comes first.
        assertTrue(IoriLogin.isAppPasswordRefusal("""{"ocs":{"meta":{"status":"failure","statuscode":403,"message":"Password confirmation is required"},"data":[]}}"""))
        // No login password to read back (single sign-on), or a proxy in the way: not an app password.
        assertFalse(IoriLogin.isAppPasswordRefusal("""{"ocs":{"meta":{"status":"failure","statuscode":403,"message":""},"data":[]}}"""))
        assertFalse(IoriLogin.isAppPasswordRefusal("""{"ocs":{"meta":{"statuscode":403},"data":[]}}"""))
        assertFalse(IoriLogin.isAppPasswordRefusal("<html>403 Forbidden</html>"))
        assertFalse(IoriLogin.isAppPasswordRefusal(""))
    }

    @Test
    fun theServersFirstAnswerSaysWhereToSignInAndWhereToAsk() {
        val body = """{"poll":{"token":"tok","endpoint":"https://cloud.example.org/login/v2/poll"},"login":"https://cloud.example.org/login/v2/flow/abc"}"""
        val started = IoriLogin.parseStart(body, "https://cloud.example.org", false)
        assertNotNull(started)
        assertEquals("tok", started!!.token)
        assertEquals("https://cloud.example.org/login/v2/poll", started.pollUrl)
        assertEquals("https://cloud.example.org/login/v2/flow/abc", started.loginUrl)
        // Not a Nextcloud's answer, or one that would send the token over plain http.
        assertNull(IoriLogin.parseStart("<html>hello</html>", "https://cloud.example.org", false))
        assertNull(IoriLogin.parseStart("{}", "https://cloud.example.org", false))
        assertNull(IoriLogin.parseStart(body.replace("https://cloud.example.org/login/v2/poll", "http://cloud.example.org/login/v2/poll"), "https://cloud.example.org", false))
        // The typed host in another case is the same host.
        assertNotNull(IoriLogin.parseStart(body, "https://Cloud.Example.org", false))
    }

    @Test
    fun theServersFirstAnswerHasToStayOnTheTypedHost() {
        val body = """{"poll":{"token":"tok","endpoint":"https://cloud.example.org/login/v2/poll"},"login":"https://cloud.example.org/login/v2/flow/abc"}"""
        assertNull(IoriLogin.parseStart(body, "https://typed.example.org", false))
        assertNull(IoriLogin.parseStart(body.replace("https://cloud.example.org/login/v2/poll", "https://other.example/login/v2/poll"), "https://cloud.example.org", false))
        assertNull(IoriLogin.parseStart(body.replace("https://cloud.example.org/login/v2/flow", "https://other.example/login/v2/flow"), "https://cloud.example.org", false))
        assertNull(IoriLogin.parseStart(body.replace("https://cloud.example.org/login/v2/poll", "https://cloud.example.org:8443/login/v2/poll"), "https://cloud.example.org", false))
        assertNotNull(IoriLogin.parseStart(body.replace("https://cloud.example.org/login/v2/poll", "https://cloud.example.org:443/login/v2/poll"), "https://cloud.example.org", false))
    }

    @Test
    fun theServersSecondAnswerIsTheAccount() {
        val body = """{"server":"https://cloud.example.org/","loginName":"ada","appPassword":"app-password-one"}"""
        assertEquals(ada, IoriLogin.parsePoll(body, "https://cloud.example.org", false))
        // A server on another host than the one typed is not kept: the typed one is.
        assertEquals("https://typed.example.org", IoriLogin.parsePoll(body, "https://typed.example.org", false)!!.server)
        // On the typed host it is, so its own path (and spelling) comes with it.
        val under = body.replace("https://cloud.example.org/", "https://cloud.example.org/nextcloud")
        assertEquals("https://cloud.example.org/nextcloud", IoriLogin.parsePoll(under, "https://Cloud.example.org", false)!!.server)
        // A server that names itself by plain http is kept at the address that was asked.
        val plain = body.replace("https://cloud.example.org/", "http://cloud.example.org")
        assertEquals("https://typed.example.org", IoriLogin.parsePoll(plain, "https://typed.example.org/", false)!!.server)
        assertNull(IoriLogin.parsePoll("""{"server":"https://cloud.example.org","loginName":"ada","appPassword":""}""", "https://typed.example.org", false))
        assertNull(IoriLogin.parsePoll("[]", "https://typed.example.org", false))
    }

    @Test
    fun theUserIdIsReadFromTheServersAnswer() {
        val body = """{"ocs":{"meta":{"status":"ok","statuscode":200},"data":{"id":"anna","displayname":"Anna","email":"anna@example.org"}}}"""
        assertEquals("anna", IoriLogin.parseUserId(body))
        assertNull(IoriLogin.parseUserId("""{"ocs":{"data":{"id":""}}}"""))
        assertNull(IoriLogin.parseUserId("""{"ocs":{"data":[]}}"""))
        assertNull(IoriLogin.parseUserId("<html>hello</html>"))
        assertNull(IoriLogin.parseUserId(""))
    }

    @Test
    fun anAppPasswordIsGoneOnlyWhenTheServerSaysSo() {
        assertTrue(IoriLogin.revokedBy(200))
        assertTrue(IoriLogin.revokedBy(401))
        assertTrue(IoriLogin.revokedBy(404))
        assertFalse(IoriLogin.revokedBy(403))
        assertFalse(IoriLogin.revokedBy(500))
        assertFalse(IoriLogin.revokedBy(502))
    }

    @Test
    fun aServerIsWrittenAsTheAppsWriteIt() {
        assertEquals("https://cloud.example.org", IoriLogin.canonical("HTTPS://Cloud.Example.ORG/"))
        assertEquals("https://cloud.example.org/NextCloud", IoriLogin.canonical("https://cloud.example.org:443/NextCloud"))
        assertEquals("http://10.0.2.2:8085", IoriLogin.canonical("http://10.0.2.2:8085"))
        assertEquals("http://localhost", IoriLogin.canonical("http://LOCALHOST:80"))
        assertEquals("http://[::1]:8080", IoriLogin.canonical("http://[::1]:8080/"))
    }

    @Test
    fun theSameHostWhateverItsCaseAndDefaultPort() {
        assertTrue(IoriLogin.sameHost("https://cloud.example.org/a", "https://CLOUD.example.org:443"))
        assertTrue(IoriLogin.sameHost("http://10.0.2.2:8085/x", "http://10.0.2.2:8085"))
        assertFalse(IoriLogin.sameHost("https://cloud.example.org", "https://cloud.example.org:8443"))
        assertFalse(IoriLogin.sameHost("https://cloud.example.org", "https://other.example"))
        assertFalse(IoriLogin.sameHost("not an address", "not an address"))
    }

    @Test
    fun theHostIsWhatIsShown() {
        assertEquals("cloud.example.org", IoriLogin.host("https://cloud.example.org/nextcloud"))
        assertEquals("10.0.2.2:8085", IoriLogin.host("http://10.0.2.2:8085"))
    }

    @Test
    fun theWebViewShellIsNotABrowser() {
        assertTrue(IoriLogin.isBrowser("org.mozilla.fennec_fdroid", "com.wanderwildwood.iori"))
        assertFalse(IoriLogin.isBrowser("org.chromium.webview_shell", "com.wanderwildwood.iori"))
        assertFalse(IoriLogin.isBrowser("com.wanderwildwood.iori", "com.wanderwildwood.iori"))
        assertFalse(IoriLogin.isBrowser(null, "com.wanderwildwood.iori"))
    }
}
