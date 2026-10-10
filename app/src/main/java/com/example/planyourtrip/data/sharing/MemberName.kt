package com.example.planyourtrip.data.sharing

import com.example.planyourtrip.data.auth.AuthUser

/** Used when the account has no display name (the email, or a part of it, is never stored). */
const val MEMBER_FALLBACK_NAME = "Trip member"

/** firestore.rules accept member names of 1–100 characters. */
private const val MAX_NAME_LENGTH = 100

/**
 * The name other members see (member documents and invites): the account's display name, never its email (not even
 * the part before "@", which `greetingName()` would use); [MEMBER_FALLBACK_NAME] when there is none.
 */
fun memberName(user: AuthUser): String =
    user.displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME_LENGTH) ?: MEMBER_FALLBACK_NAME
