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
        assertFalse(memory.text!!.contains("ada"))
        assertFalse(memory.text!!.contains("cloud.example.org"))
        // Nothing left to forget: no second change is announced.
        assertFalse(s.signOut())
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
    fun theServersFirstAnswerSaysWhereToSignInAndWhereToAsk() {
        val body = """{"poll":{"token":"tok","endpoint":"https://cloud.example.org/login/v2/poll"},"login":"https://cloud.example.org/login/v2/flow/abc"}"""
        val started = IoriLogin.parseStart(body, false)
        assertNotNull(started)
        assertEquals("tok", started!!.token)
        assertEquals("https://cloud.example.org/login/v2/poll", started.pollUrl)
        assertEquals("https://cloud.example.org/login/v2/flow/abc", started.loginUrl)
        // Not a Nextcloud's answer, or one that would send the token over plain http.
        assertNull(IoriLogin.parseStart("<html>hello</html>", false))
        assertNull(IoriLogin.parseStart("{}", false))
        assertNull(IoriLogin.parseStart(body.replace("https://cloud.example.org/login/v2/poll", "http://cloud.example.org/login/v2/poll"), false))
    }

    @Test
    fun theServersSecondAnswerIsTheAccount() {
        val body = """{"server":"https://cloud.example.org/","loginName":"ada","appPassword":"app-password-one"}"""
        assertEquals(ada, IoriLogin.parsePoll(body, "https://typed.example.org", false))
        // A server that names itself by plain http is kept at the address that was asked.
        val plain = body.replace("https://cloud.example.org/", "http://cloud.example.org")
        assertEquals("https://typed.example.org", IoriLogin.parsePoll(plain, "https://typed.example.org/", false)!!.server)
        assertNull(IoriLogin.parsePoll("""{"server":"https://cloud.example.org","loginName":"ada","appPassword":""}""", "https://typed.example.org", false))
        assertNull(IoriLogin.parsePoll("[]", "https://typed.example.org", false))
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
