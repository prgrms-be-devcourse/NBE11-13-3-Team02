package com.gachisa.queue.dto

data class ExpiredAdmission(
    val userId: Long,
    val paymentAttemptId: Long?,
)
