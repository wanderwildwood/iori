package com.wanderwildwood.iori

import com.github.gezimos.inkos.R
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IoriAppsTest {

    private fun app(pkg: String, core: Boolean) = app(pkg, if (core) IoriApps.CORE else "tools")

    private fun app(pkg: String, section: String) = IoriApps.App(pkg, pkg, "00", section)

    @Test
    fun appsAreListedUnderTheirSectionsInOrderAndNoneIsLost() {
        val apps = listOf(app("a", "music"), app("b", IoriApps.CORE), app("c", "not known yet"), app("d", "tools"))
        val listed = IoriApps.sections(apps)
        assertEquals(listOf(listOf("b"), listOf("a"), listOf("c", "d")), listed.map { s -> s.second.map { it.pkg } })
        assertEquals(apps.size, listed.sumOf { it.second.size })
    }

    @Test
    fun theSitesAppsAreFoldedByTheSectionHomeGivesThem() {
        // Your apps folds the entries of the list on wanderthe.dev by a section looked up per package.
        val of = mapOf("x" to "games", "y" to IoriApps.CORE, "z" to "something new")
        val listed = IoriApps.sections(listOf("x", "y", "z")) { of.getValue(it) }
        assertEquals(listOf(R.string.iori_apps_core, R.string.iori_apps_games, R.string.iori_apps_tools), listed.map { it.first })
        assertEquals(listOf(listOf("y"), listOf("x"), listOf("z")), listed.map { it.second })
    }

    @Test
    fun aFirstRunTicksTheCoreAppsThatAreMissing() {
        val apps = listOf(app("a", true), app("b", true), app("c", false), app("d", false))
        assertEquals(setOf("b"), IoriApps.firstPick(apps, installed = setOf("a", "d")))
        assertEquals(setOf("a", "b"), IoriApps.firstPick(apps, installed = emptySet()))
        assertEquals(emptySet<String>(), IoriApps.firstPick(apps, installed = setOf("a", "b")))
    }

    // --- Which list Your apps shows: fetched, else kept, else Home's own ---

    private fun entry(pkg: String) = IoriAppsManifest.Entry(
        pkg = pkg, name = pkg, summary = "", core = true, versionName = "1", versionCode = 1,
        apk = "https://github.com/wanderwildwood/x/releases/download/v1/x.apk",
        sha256 = "a".repeat(64), certSha256 = "b".repeat(64), minSdk = 1, size = 1,
    )

    private val fetched = listOf(entry("fetched"))
    private val kept = listOf(entry("kept"))
    private val ours = listOf(app("ours", true))

    @Test
    fun theListJustFetchedIsShownBeforeAnyOther() {
        assertEquals(IoriAppInstaller.ListState.Ready(fetched, fromCopy = false, problem = null), IoriAppInstaller.ListState.of(fetched, kept, ours, null))
        assertEquals(IoriAppInstaller.ListState.Ready(fetched, fromCopy = false, problem = null), IoriAppInstaller.ListState.of(fetched, null, ours, null))
    }

    @Test
    fun withNoFetchTheKeptCopyIsShownWithWhy() {
        assertEquals(IoriAppInstaller.ListState.Ready(kept, fromCopy = true, problem = "offline"), IoriAppInstaller.ListState.of(null, kept, ours, "offline"))
        // An empty kept copy is still a list from wanderthe.dev, and comes before Home's own.
        assertEquals(IoriAppInstaller.ListState.Ready(emptyList(), fromCopy = true, problem = "offline"), IoriAppInstaller.ListState.of(null, emptyList(), ours, "offline"))
    }

    @Test
    fun withNeitherHomesOwnListIsShownAndNothingCanBeInstalledFromIt() {
        val shown = IoriAppInstaller.ListState.of(null, null, ours, "offline")
        assertEquals(IoriAppInstaller.ListState.BuiltIn(ours, "offline"), shown)
        // Home's own list carries no Entry (no download address, size or checksum) to install from.
        assertFalse(shown is IoriAppInstaller.ListState.Ready)
    }

    @Test
    fun withNoListAtAllTheReasonIsShownAlone() {
        assertEquals(IoriAppInstaller.ListState.Failed("offline"), IoriAppInstaller.ListState.of(null, null, emptyList(), "offline"))
    }

    @Test
    fun homesOwnListGivesEveryAppANameAndAKnownSection() {
        val known = IoriApps.SECTIONS.map { it.first }.toSet()
        bundled.forEach { o ->
            val pkg = o.get("package").asString
            assertTrue("$pkg has no name", o.get("name")?.asString.orEmpty().isNotBlank())
            assertTrue("$pkg has an unknown section", o.get("section")?.asString in known)
        }
    }

    @Test
    fun aDefaultIsOursAskedOrAnothers() {
        val ours = "com.wanderwildwood.tokei"
        assertEquals("ours", IoriDefaults.whose(ours, ours))
        assertEquals("ours", IoriDefaults.whose("$ours.debug", ours))
        assertEquals("ask", IoriDefaults.whose("android", ours))
        assertEquals("other", IoriDefaults.whose("com.android.deskclock", ours))
        assertEquals("none", IoriDefaults.whose(null, ours))
    }

    private val bundled by lazy {
        JsonParser.parseString(File("src/iori/assets/iori_apps.json").readText()).asJsonObject.getAsJsonArray("apps").map { it.asJsonObject }
    }

    @Test
    fun everyAppInHomesListCarriesTheOldestReleaseHomeTakes() {
        bundled.forEach { o ->
            val pkg = o.get("package").asString
            assertTrue("$pkg has no minVersionCode", (o.get("minVersionCode")?.asLong ?: 0L) > 0L)
        }
    }

    @Test
    fun glancesPinnedCertificateIsTheOneInHomesList() {
        val listed = bundled.single { it.get("package").asString == IoriLinesRepository.GLANCE }.get("cert").asString.lowercase()
        assertEquals(listed, IoriLinesRepository.GLANCE_CERT)
    }

    @Test
    fun linesAreReadOnlyFromGlanceSignedWithItsOwnKey() {
        val glance = IoriLinesRepository.GLANCE
        val cert = IoriLinesRepository.GLANCE_CERT
        val debug = IoriAccount.DEBUG_CERTS.first()
        assertTrue(isGlance(glance, listOf(cert), debugBuild = false))
        assertTrue(isGlance(glance, listOf(cert.uppercase()), debugBuild = false))
        // Another app holding the panel's authority, whatever it is signed with.
        assertFalse(isGlance("com.evil.app", listOf(cert), debugBuild = false))
        assertFalse(isGlance(null, listOf(cert), debugBuild = false))
        // Glance's name with another key, a second key beside it, or no key read at all.
        assertFalse(isGlance(glance, listOf("0".repeat(64)), debugBuild = false))
        assertFalse(isGlance(glance, listOf(cert, "0".repeat(64)), debugBuild = false))
        assertFalse(isGlance(glance, emptyList(), debugBuild = false))
        // A debug key only for a debug Home.
        assertFalse(isGlance(glance, listOf(debug), debugBuild = false))
        assertTrue(isGlance(glance, listOf(debug), debugBuild = true))
    }
}
