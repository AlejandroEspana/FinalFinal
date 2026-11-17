package com.empresa.mantenimiento.application.dto;

import java.time.Instant;

public record NotificationRecordDto(
        String notificationId,
        String channel,
        String recipient,
        String subject,
        String messageBody,
        String deliveryStatus,
        String whatsappDirectUrl,
        String pushTopicUrl,
        Instant sentAt
) {
}
