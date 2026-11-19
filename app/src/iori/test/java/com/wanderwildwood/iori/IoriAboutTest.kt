package com.wanderwildwood.iori

import com.github.gezimos.inkos.R
import org.junit.Assert.assertEquals
import org.junit.Test

class IoriAboutTest {

    @Test
    fun eachLicenseLineGetsItsFullText() {
        assertEquals(listOf(R.raw.apache_2_0), IoriAbout.textsFor("Apache-2.0"))
        assertEquals(listOf(R.raw.ofl_license), IoriAbout.textsFor("OFL-1.1"))
        assertEquals(listOf(R.raw.glide_license, R.raw.apache_2_0), IoriAbout.textsFor("BSD-2-Clause, Apache-2.0"))
        assertEquals(emptyList<Int>(), IoriAbout.textsFor("MIT"))
    }
}
