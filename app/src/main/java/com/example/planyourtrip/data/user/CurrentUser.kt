package com.example.planyourtrip.data.user

import com.example.planyourtrip.data.auth.AuthUser

/**
 * Name used for the greeting, avatar initials and account menu of the signed-in user:
 * the display name, or the part of the email before "@" when there is no name, or "".
 * The signed-in user itself comes from `AuthRepository.currentUser`.
 */
fun AuthUser.greetingName(): String =
    displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: ""
