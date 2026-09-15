package com.gachisa.auth.dto

data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)
