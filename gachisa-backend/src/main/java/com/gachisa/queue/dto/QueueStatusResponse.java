package com.gachisa.queue.dto;

import java.time.LocalDateTime;

public record QueueStatusResponse(
        QueueState status,
        Long position,
        LocalDateTime admissionExpiresAt
) {
}
