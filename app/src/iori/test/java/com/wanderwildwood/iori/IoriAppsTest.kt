package com.wanderwildwood.iori

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
}
