package com.gachisa.auth.dto

data class SignUpResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: String,
)
