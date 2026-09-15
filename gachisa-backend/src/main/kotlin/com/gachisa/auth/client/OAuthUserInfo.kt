package com.gachisa.auth.client

data class OAuthUserInfo(
    val providerId: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String,
)
