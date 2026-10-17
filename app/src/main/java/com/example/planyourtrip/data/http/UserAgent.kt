package com.example.planyourtrip.data.http

/**
 * Sent with every HTTP request and every photo download: Wikimedia's and Photon's usage policies ask every client to
 * identify itself with a contact.
 */
fun appUserAgent(appVersion: String): String =
    "PlanYourTrip/$appVersion (https://github.com/Etay0109/PlanYourTrip; Android; com.example.planyourtrip)"
