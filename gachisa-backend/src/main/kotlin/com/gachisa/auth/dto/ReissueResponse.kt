package com.gachisa.auth.dto

data class ReissueResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)
