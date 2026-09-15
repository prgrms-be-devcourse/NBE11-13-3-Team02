package com.gachisa.auth.dto

data class LoginResult(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val rawRefreshToken: String,
)
