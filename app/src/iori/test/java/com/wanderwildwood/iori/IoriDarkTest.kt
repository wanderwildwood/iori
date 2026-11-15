package com.wanderwildwood.iori

import org.junit.Assert.assertEquals
import org.junit.Test

class IoriDarkTest {

    @Test
    fun whereTheSystemHasNoDarkSettingTheProviderSaysHomesSwitch() {
        assertEquals(1, IoriDark.answer(switchOn = true, systemHasSetting = false))
        assertEquals(0, IoriDark.answer(switchOn = false, systemHasSetting = false))
    }

    @Test
    fun whereTheSystemHasOneTheProviderNeverSaysDark() {
        // A switch left on from before would otherwise hold every app dark.
        assertEquals(0, IoriDark.answer(switchOn = true, systemHasSetting = true))
        assertEquals(0, IoriDark.answer(switchOn = false, systemHasSetting = true))
    }
}
