package com.example.planyourtrip.ui.common

// Pure (unit-tested in DestinationNamesTest): the short destination shown next to the flag.

/**
 * What the Home cards and the trip header show next to the flag: for a picked place ([countryCode] set) written as
 * "Name, Country", only the name ("London, United Kingdom" → "London"). It cuts at the **last** ", ", so a name with a
 * comma stays whole. Without a country code, without ", " or when nothing would be left, the full text as it is.
 * Display only: the stored destination, the form, the PDF, invites, the assistant and TalkBack keep the full text.
 */
fun shortDestination(destination: String, countryCode: String?): String {
    if (countryFlag(countryCode) == null) return destination
    val cut = destination.lastIndexOf(", ")
    if (cut < 0) return destination
    return destination.substring(0, cut).trim().ifEmpty { destination }
}
