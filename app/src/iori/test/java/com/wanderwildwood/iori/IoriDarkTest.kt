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

    @Test
    fun android12NeverCountsAsHavingADarkSettingWhateverSettingsAnswers() {
        // MuditaOS is Android 12 (API 31): Home's switch must stay there.
        assertEquals(false, IoriDark.hasDarkSetting(31) { true })
        assertEquals(false, IoriDark.hasDarkSetting(32) { true })
        assertEquals(true, IoriDark.hasDarkSetting(36) { true })
        assertEquals(false, IoriDark.hasDarkSetting(36) { false })
    }
}
