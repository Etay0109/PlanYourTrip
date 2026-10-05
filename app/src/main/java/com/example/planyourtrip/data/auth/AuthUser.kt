package com.example.planyourtrip.data.auth

/** The signed-in user, independent of the auth backend. */
data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
)
