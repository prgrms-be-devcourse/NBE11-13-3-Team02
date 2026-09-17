package com.gachisa.payment.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class GroupBuyResultCommand(
    @field:NotNull val groupBuyId: Long,
    @field:NotNull val result: Result,
    @field:NotEmpty @field:Size(max = 1000) val participationIds: List<@NotNull Long>,
) {
    enum class Result {
        ACHIEVED,
        FAILED,
    }
}
