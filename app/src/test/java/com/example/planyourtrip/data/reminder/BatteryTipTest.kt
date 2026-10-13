package com.example.planyourtrip.data.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTipTest {
    @Test
    fun offeredOnce_onBrandsThatLimitApps_whileTheAppIsntExempt() {
        assertTrue(shouldOfferBatteryTip("samsung", exempt = false, alreadyShown = false))
        assertTrue(shouldOfferBatteryTip("Xiaomi", exempt = false, alreadyShown = false))
        assertTrue(shouldOfferBatteryTip(" OnePlus ", exempt = false, alreadyShown = false))
        assertFalse(shouldOfferBatteryTip("Google", exempt = false, alreadyShown = false))
        assertFalse(shouldOfferBatteryTip("samsung", exempt = true, alreadyShown = false))
        assertFalse(shouldOfferBatteryTip("samsung", exempt = false, alreadyShown = true))
    }

    @Test
    fun samsungGetsItsOwnSteps() {
        assertTrue(isSamsung("SAMSUNG"))
        assertFalse(isSamsung("xiaomi"))
    }
}
