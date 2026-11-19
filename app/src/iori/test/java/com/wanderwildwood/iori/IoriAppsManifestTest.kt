package com.wanderwildwood.iori

import com.wanderwildwood.iori.IoriAppsManifest.Facts
import com.wanderwildwood.iori.IoriAppsManifest.Installed
import com.wanderwildwood.iori.IoriAppsManifest.Status
import com.wanderwildwood.iori.IoriAppsManifest.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IoriAppsManifestTest {

    private val sha = "a".repeat(64)
    private val cert = "b".repeat(64)
    private val other = "c".repeat(64)

    private fun app(
        pkg: String = "com.wanderwildwood.oboegaki",
        core: Any? = true,
        apk: String = "https://github.com/wanderwildwood/oboegaki/releases/download/v1.2.3/oboegaki.apk",
        extra: String = "",
    ) = """{"package":"$pkg","name":"Memo","summary":"Notes and tasks","core":${core},"versionName":"1.2.3",
        "versionCode":123,"apk":"$apk","sha256":"$sha","certSha256":"$cert","minSdk":26,"size":1000$extra}"""

    private fun manifest(vararg apps: String, version: Any = 1) = """{"version":$version,"apps":[${apps.joinToString(",")}]}"""

    // --- Parsing ---

    @Test
    fun aGoodEntryIsReadInFull() {
        val e = IoriAppsManifest.parse(manifest(app()))!!.single()
        assertEquals("com.wanderwildwood.oboegaki", e.pkg)
        assertEquals("Memo", e.name)
        assertEquals("Notes and tasks", e.summary)
        assertTrue(e.core)
        assertEquals("1.2.3", e.versionName)
        assertEquals(123L, e.versionCode)
        assertEquals(sha, e.sha256)
        assertEquals(cert, e.certSha256)
        assertEquals(26, e.minSdk)
        assertEquals(1000L, e.size)
    }

    @Test
    fun coreAppsComeFirstInTheFilesOrder() {
        val list = IoriAppsManifest.parse(
            manifest(app(pkg = "a.extra1", core = false), app(pkg = "a.core1"), app(pkg = "a.extra2", core = false), app(pkg = "a.core2"))
        )!!
        assertEquals(listOf("a.core1", "a.core2", "a.extra1", "a.extra2"), list.map { it.pkg })
    }

    @Test
    fun badEntriesAreSkippedAndTheRestKept() {
        val list = IoriAppsManifest.parse(
            manifest(
                app(pkg = "good.one"),
                app(pkg = "plain.http", apk = "http://example.com/a.apk"),
                app(pkg = "file.url", apk = "file:///sdcard/a.apk"),
                app(pkg = "no dots"),
                """{"package":"short.sha","name":"X","apk":"https://x.y/a.apk","sha256":"abc","certSha256":"$cert","versionCode":1}""",
                """{"package":"no.code","name":"X","apk":"https://x.y/a.apk","sha256":"$sha","certSha256":"$cert"}""",
                """{"package":"zero.code","name":"X","apk":"https://x.y/a.apk","sha256":"$sha","certSha256":"$cert","versionCode":0}""",
                """{"package":"huge.size","name":"X","apk":"https://x.y/a.apk","sha256":"$sha","certSha256":"$cert","versionCode":1,"size":999999999999}""",
                """{"package":"no.name","apk":"https://x.y/a.apk","sha256":"$sha","certSha256":"$cert","versionCode":1}""",
                "42",
                "null",
                "[1,2]",
                app(pkg = "good.two", core = "\"yes\""),
            )
        )!!
        assertEquals(listOf("good.one", "good.two"), list.map { it.pkg })
        // "core" is true only when it is the JSON true.
        assertFalse(list.last().core)
    }

    @Test
    fun theFirstEntryForAPackageWins() {
        val list = IoriAppsManifest.parse(manifest(app(core = false), app()))!!
        assertEquals(1, list.size)
        assertFalse(list.single().core)
    }

    @Test
    fun aCertWithColonsAndCapitalsIsRead() {
        val colons = cert.uppercase().chunked(2).joinToString(":")
        val text = manifest(app()).replace("\"$cert\"", "\"$colons\"")
        assertEquals(cert, IoriAppsManifest.parse(text)!!.single().certSha256)
    }

    @Test
    fun missingOptionalFieldsTakeSafeValues() {
        val e = IoriAppsManifest.parse(
            manifest("""{"package":"a.b","name":"X","apk":"https://x.y/a.apk","sha256":"$sha","certSha256":"$cert","versionCode":"7"}""")
        )!!.single()
        assertEquals("", e.summary)
        assertFalse(e.core)
        assertEquals("7", e.versionName)
        assertEquals(7L, e.versionCode)
        assertEquals(1, e.minSdk)
        assertEquals(0L, e.size)
    }

    @Test
    fun aBrokenOrForeignFileIsNullNeverACrash() {
        assertNull(IoriAppsManifest.parse(""))
        assertNull(IoriAppsManifest.parse("not json"))
        assertNull(IoriAppsManifest.parse("<html>404</html>"))
        assertNull(IoriAppsManifest.parse("""{"version":1,"apps":[""" + app()))
        assertNull(IoriAppsManifest.parse("""{"version":1}"""))
        assertNull(IoriAppsManifest.parse("""{"version":1,"apps":{}}"""))
        assertNull(IoriAppsManifest.parse("[]"))
        assertNull(IoriAppsManifest.parse(manifest(app(), version = 2)))
        assertNull(IoriAppsManifest.parse(manifest(app(), version = "\"1\"")))
        assertNull(IoriAppsManifest.parse("[".repeat(10_000)))
        assertNull(IoriAppsManifest.parse("""{"version":1,"apps":["\u"]}"""))
        assertNull(IoriAppsManifest.parse(manifest(app()) + " trailing"))
        assertNotNull(IoriAppsManifest.parse(manifest()))
    }

    @Test
    fun onlyHttpsAddressesPass() {
        assertTrue(IoriAppsManifest.isHttps("https://wanderthe.dev/apps.json"))
        assertTrue(IoriAppsManifest.isHttps(IoriAppsManifest.URL))
        assertFalse(IoriAppsManifest.isHttps("http://wanderthe.dev/apps.json"))
        assertFalse(IoriAppsManifest.isHttps("https://"))
        assertFalse(IoriAppsManifest.isHttps("https:///path"))
        assertFalse(IoriAppsManifest.isHttps("https://user@evil.example/a.apk"))
        assertFalse(IoriAppsManifest.isHttps("ftp://x.y/a.apk"))
        assertFalse(IoriAppsManifest.isHttps("content://x/a.apk"))
    }

    // --- Version comparison and the button ---

    @Test
    fun versionCodesDecideWhenBothAreSet() {
        assertTrue(IoriAppsManifest.isNewer(124, "1.2.3", 123, "9.9.9"))
        assertFalse(IoriAppsManifest.isNewer(123, "9.9.9", 123, "1.0"))
        assertFalse(IoriAppsManifest.isNewer(122, "2.0", 123, "1.0"))
    }

    @Test
    fun namesDecideNumberByNumberWithoutCodes() {
        assertTrue(IoriAppsManifest.isNewer(0, "1.10.0", 0, "1.9.3"))
        assertTrue(IoriAppsManifest.isNewer(0, "1.2.1", 0, "1.2"))
        assertFalse(IoriAppsManifest.isNewer(0, "1.2", 0, "1.2.0"))
        assertFalse(IoriAppsManifest.isNewer(0, "1.35.33", 0, "1.35.33-test"))
        assertTrue(IoriAppsManifest.isNewer(0, "v0.10.0", 0, "0.9.12"))
        assertFalse(IoriAppsManifest.isNewer(0, "", 0, ""))
        assertEquals(0, IoriAppsManifest.compareNames("1.0+build5", "1.0"))
        assertTrue(IoriAppsManifest.compareNames("0.9", "0.10") < 0)
    }

    @Test
    fun theButtonFollowsWhatIsOnThePhone() {
        val e = IoriAppsManifest.parse(manifest(app()))!!.single()
        assertEquals(Status.NOT_INSTALLED, IoriAppsManifest.status(e, null, 31))
        assertEquals(Status.UPDATE, IoriAppsManifest.status(e, Installed("1.2.2", 122), 31))
        assertEquals(Status.UP_TO_DATE, IoriAppsManifest.status(e, Installed("1.2.3", 123), 31))
        assertEquals(Status.UP_TO_DATE, IoriAppsManifest.status(e, Installed("1.3.0", 130), 31))
        assertEquals(Status.TOO_OLD_ANDROID, IoriAppsManifest.status(e, null, 25))
        assertEquals(Status.TOO_OLD_ANDROID, IoriAppsManifest.status(e, Installed("1.2.2", 122), 25))
        // An app that is up to date stays so on any Android.
        assertEquals(Status.UP_TO_DATE, IoriAppsManifest.status(e, Installed("1.2.3", 123), 25))
    }

    // --- May this file be installed ---

    private val entry get() = IoriAppsManifest.parse(manifest(app()))!!.single()

    private fun facts(
        size: Long = 1000,
        sha256: String = sha,
        pkg: String? = "com.wanderwildwood.oboegaki",
        versionCode: Long = 123,
        signers: List<String> = listOf(cert),
        installedSigners: List<String>? = null,
        installedVersionCode: Long? = null,
        pinned: String? = null,
    ) = Facts(size, sha256, pkg, versionCode, signers, installedSigners, installedVersionCode, pinned)

    @Test
    fun aFileThatMatchesInEveryWayIsInstalled() {
        assertEquals(Verdict.OK, IoriAppsManifest.verify(entry, facts()))
        assertEquals(Verdict.OK, IoriAppsManifest.verify(entry, facts(sha256 = sha.uppercase(), pinned = cert)))
        assertEquals(Verdict.OK, IoriAppsManifest.verify(entry, facts(installedSigners = listOf(cert), installedVersionCode = 122)))
    }

    @Test
    fun eachMismatchIsRefusedForItsOwnReason() {
        assertEquals(Verdict.WRONG_SIZE, IoriAppsManifest.verify(entry, facts(size = 999)))
        assertEquals(Verdict.WRONG_CHECKSUM, IoriAppsManifest.verify(entry, facts(sha256 = "d".repeat(64))))
        assertEquals(Verdict.NOT_AN_APP, IoriAppsManifest.verify(entry, facts(pkg = null)))
        assertEquals(Verdict.WRONG_APP, IoriAppsManifest.verify(entry, facts(pkg = "com.evil.app")))
        assertEquals(Verdict.WRONG_VERSION, IoriAppsManifest.verify(entry, facts(versionCode = 122)))
        assertEquals(Verdict.NOT_PINNED, IoriAppsManifest.verify(entry, facts(pinned = other)))
    }

    @Test
    fun theFileMustBeSignedWithTheListedCertificateAlone() {
        assertEquals(Verdict.WRONG_KEY, IoriAppsManifest.verify(entry, facts(signers = emptyList())))
        assertEquals(Verdict.WRONG_KEY, IoriAppsManifest.verify(entry, facts(signers = listOf(other))))
        assertEquals(Verdict.WRONG_KEY, IoriAppsManifest.verify(entry, facts(signers = listOf(cert, other))))
        // The same certificate twice is still that one certificate alone.
        assertEquals(Verdict.OK, IoriAppsManifest.verify(entry, facts(signers = listOf(cert, cert.uppercase()))))
    }

    @Test
    fun anUpdateMustBeSignedLikeTheAppOnThePhoneAndBeNewer() {
        assertEquals(Verdict.KEY_CHANGED, IoriAppsManifest.verify(entry, facts(installedSigners = listOf(other), installedVersionCode = 1)))
        assertEquals(Verdict.KEY_CHANGED, IoriAppsManifest.verify(entry, facts(installedSigners = emptyList(), installedVersionCode = 1)))
        assertEquals(Verdict.NOT_NEWER, IoriAppsManifest.verify(entry, facts(installedSigners = listOf(cert), installedVersionCode = 123)))
        assertEquals(Verdict.NOT_NEWER, IoriAppsManifest.verify(entry, facts(installedSigners = listOf(cert), installedVersionCode = 200)))
    }

    @Test
    fun aSizeOfNoneIsNotCheckedButEverythingElseIs() {
        val noSize = IoriAppsManifest.parse(manifest(app().replace("\"size\":1000", "\"size\":0")))!!.single()
        assertEquals(Verdict.OK, IoriAppsManifest.verify(noSize, facts(size = 5)))
        assertEquals(Verdict.WRONG_CHECKSUM, IoriAppsManifest.verify(noSize, facts(size = 5, sha256 = other)))
    }
}
