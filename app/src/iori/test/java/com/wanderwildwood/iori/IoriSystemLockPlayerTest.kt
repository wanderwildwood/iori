package com.wanderwildwood.iori

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IoriSystemLockPlayerTest {
    @Test fun muditaOs1HasNoPlayerForOtherApps() {
        assertFalse(IoriSystemLockPlayer.presentFor("MuditaOS K 1.6.1"))
        assertFalse(IoriSystemLockPlayer.presentFor("MuditaOS K 1.0"))
    }

    @Test fun muditaOs2AndLaterHaveOne() {
        assertTrue(IoriSystemLockPlayer.presentFor("MuditaOS K 2.0.0"))
        assertTrue(IoriSystemLockPlayer.presentFor("MuditaOS K 10.1"))
    }

    @Test fun anyOtherAndroidHasOne() {
        assertTrue(IoriSystemLockPlayer.presentFor("BP2A.250605.031"))
        assertTrue(IoriSystemLockPlayer.presentFor(""))
    }
}
