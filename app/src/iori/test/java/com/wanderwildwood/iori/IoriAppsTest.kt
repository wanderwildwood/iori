package com.wanderwildwood.iori

import com.github.gezimos.inkos.R
import org.junit.Assert.assertEquals
import org.junit.Test

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
