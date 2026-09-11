package com.gachisa.payment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GroupBuyResultCommand(
        @NotNull Long groupBuyId,
        @NotNull Result result,
        @NotEmpty @Size(max = 1000) List<@NotNull Long> participationIds
) {

    public enum Result {
        ACHIEVED,
        FAILED
    }
}
