package com.example.planyourtrip.data.reminder

// Pure (unit-tested in BatteryTipTest): who sees the one-time "Get trip reminders on time" battery screen.

/**
 * Brands whose battery savers are known to pause or put to sleep apps that aren't in use (`Build.MANUFACTURER`,
 * lowercase), which delays or blocks reminders. Other phones (Pixel, …) never see the screen.
 */
val LIMITING_BRANDS: Set<String> =
    setOf("samsung", "xiaomi", "redmi", "poco", "huawei", "honor", "oneplus", "oppo", "realme", "vivo")

/**
 * The battery screen is offered once, only on [LIMITING_BRANDS], and only while the app isn't already [exempt] from
 * battery optimization.
 */
fun shouldOfferBatteryTip(manufacturer: String, exempt: Boolean, alreadyShown: Boolean): Boolean =
    !alreadyShown && !exempt && manufacturer.trim().lowercase() in LIMITING_BRANDS

/** Samsung gets its own steps ("Never sleeping apps"). */
fun isSamsung(manufacturer: String): Boolean = manufacturer.trim().equals("samsung", ignoreCase = true)
