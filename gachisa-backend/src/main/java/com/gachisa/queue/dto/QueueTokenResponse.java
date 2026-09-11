package com.gachisa.queue.dto;

import java.time.LocalDateTime;

public record QueueTokenResponse(
        String queueToken,
        QueueState status,
        Long position,
        LocalDateTime admissionExpiresAt
) {
}
