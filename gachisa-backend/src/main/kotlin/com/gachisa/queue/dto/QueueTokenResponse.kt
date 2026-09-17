package com.gachisa.queue.dto

import java.time.LocalDateTime

data class QueueTokenResponse(
    val queueToken: String,
    val status: QueueState,
    val position: Long?,
    val admissionExpiresAt: LocalDateTime?,
)
