package com.clouddocs.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record CreateNotificationRequest(
            @NotBlank @Size(max = 150) String title, @NotBlank @Size(max = 1000) String message) {}

    public record NotificationResponse(
            Long id, String title, String message, String createdBy, Instant createdAt, Long workTaskId, boolean canDelete) {}
}
