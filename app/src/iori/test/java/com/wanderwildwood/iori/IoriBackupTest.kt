package com.wanderwildwood.iori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IoriBackupTest {

    private val own = "com.wanderwildwood.iori"

    @Test
    fun internalSlotFollowsTheLauncher() {
        val json = """{"APP_PACKAGE_0":"app.inkos","APP_SHORTCUT_ID_0":"inkos_app_drawer"}"""
        assertEquals(
            """{"APP_PACKAGE_0":"$own","APP_SHORTCUT_ID_0":"inkos_app_drawer"}""",
            IoriBackup.rewrite(json, own),
        )
    }

    @Test
    fun hiddenInternalEntriesFollowTheLauncher() {
        val json = """{"HIDDEN_APPS":["app.inkos|inkos_notes|0","org.example.app|0"]}"""
        assertEquals(
            """{"HIDDEN_APPS":["$own|inkos_notes|0","org.example.app|0"]}""",
            IoriBackup.rewrite(json, own),
        )
    }

    @Test
    fun keysAndOtherPackagesAreLeftAlone() {
        val json = """{"app_alias_app.inkos":"Home","APP_PACKAGE_1":"app.inkos.debug","APP_PACKAGE_2":"app.inkosx"}"""
        assertEquals(json, IoriBackup.rewrite(json, own))
    }

    @Test
    fun inkOSItselfIsNotRewritten() {
        val json = """{"APP_PACKAGE_0":"app.inkos"}"""
        assertEquals(json, IoriBackup.rewrite(json, "app.inkos"))
    }

    @Test
    fun guardKnowsItsOwnFrames() {
        val ours = RuntimeException("x").apply {
            stackTrace = arrayOf(StackTraceElement("com.wanderwildwood.iori.IoriLinesKt", "IoriLines", "IoriLines.kt", 1))
        }
        val theirs = RuntimeException("y").apply {
            stackTrace = arrayOf(StackTraceElement("com.github.gezimos.inkos.MainActivity", "onResume", "MainActivity.kt", 1))
        }
        assertTrue(Iori.fromOwnCode(ours))
        assertFalse(Iori.fromOwnCode(theirs))
        assertTrue(Iori.fromOwnCode(RuntimeException("wrapped", ours).apply { stackTrace = theirs.stackTrace }))
    }
}
