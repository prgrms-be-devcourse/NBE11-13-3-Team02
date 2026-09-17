package com.gachisa.queue.dto

import java.time.LocalDateTime

data class QueueStatusResponse(
    val status: QueueState,
    val position: Long?,
    val admissionExpiresAt: LocalDateTime?,
)
