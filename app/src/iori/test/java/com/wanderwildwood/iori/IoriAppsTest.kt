package com.wanderwildwood.iori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IoriAppsTest {

    @Test
    fun aHigherReleaseIsNewer() {
        assertTrue(IoriApps.newer("1.35.35", "1.35.34"))
        assertTrue(IoriApps.newer("1.36.0", "1.35.99"))
        assertTrue(IoriApps.newer("0.10.0", "0.9.12"))
        assertTrue(IoriApps.newer("1.2.1", "1.2"))
    }

    @Test
    fun theSameOrAnOlderReleaseIsNot() {
        assertFalse(IoriApps.newer("1.35.35", "1.35.35"))
        assertFalse(IoriApps.newer("1.35.34", "1.35.35"))
        assertFalse(IoriApps.newer("0.9.12", "0.10.0"))
    }

    @Test
    fun aTestBuildOfTheSameVersionIsNotBehind() {
        assertFalse(IoriApps.newer("1.35.33", "1.35.33-test"))
        assertTrue(IoriApps.newer("1.35.34", "1.35.33-test"))
    }

    private fun app(pkg: String, core: Boolean) = app(pkg, if (core) IoriApps.CORE else "tools")

    private fun app(pkg: String, section: String) = IoriApps.App(pkg, pkg, "o/$pkg", "$pkg.apk", "00", section)

    @Test
    fun appsAreListedUnderTheirSectionsInOrderAndNoneIsLost() {
        val apps = listOf(app("a", "music"), app("b", IoriApps.CORE), app("c", "not known yet"), app("d", "tools"))
        val listed = IoriApps.sections(apps)
        assertEquals(listOf(listOf("b"), listOf("a"), listOf("c", "d")), listed.map { s -> s.second.map { it.pkg } })
        assertEquals(apps.size, listed.sumOf { it.second.size })
    }

    @Test
    fun aFirstRunTicksTheCoreAppsThatAreMissing() {
        val apps = listOf(app("a", true), app("b", true), app("c", false), app("d", false))
        assertEquals(setOf("b"), IoriApps.firstPick(apps, installed = setOf("a", "d")))
        assertEquals(setOf("a", "b"), IoriApps.firstPick(apps, installed = emptySet()))
        assertEquals(emptySet<String>(), IoriApps.firstPick(apps, installed = setOf("a", "b")))
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
}
