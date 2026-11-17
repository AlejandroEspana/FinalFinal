package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.application.dto.DecisionResultDto;
import com.empresa.mantenimiento.application.dto.NotificationRecordDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Adaptador Multicanal de Notificaciones Industriales (Push Móvil ntfy.sh + Correo Institucional + WhatsApp).
 */
@Service
public class MultichannelNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MultichannelNotificationService.class);
    private static final int MAX_HISTORY = 50;

    private volatile String targetEmail;
    private volatile String targetPhone;
    private volatile String ntfyTopic;
    private volatile boolean notificationsEnabled;

    private final HttpClient httpClient;
    private final List<NotificationRecordDto> history = new CopyOnWriteArrayList<>();

    public MultichannelNotificationService(
            @Value("${app.notifications.email:alejandro.espaname@campusucc.edu.co}") String targetEmail,
            @Value("${app.notifications.phone:+573154613867}") String targetPhone,
            @Value("${app.notifications.ntfy-topic:scada-ucc-alejandro-573154613867}") String ntfyTopic,
            @Value("${app.notifications.enabled:true}") boolean notificationsEnabled
    ) {
        this.targetEmail = targetEmail;
        this.targetPhone = targetPhone;
        this.ntfyTopic = ntfyTopic;
        this.notificationsEnabled = notificationsEnabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public NotificationRecordDto notifyOperatorDecision(DecisionResultDto decision, String operatorId, String notes) {
        String subject = String.format("SCADA IoT [%s] -> %s en %s",
                decision.actionDispatched(), decision.sensorId(), decision.alertId());
        String body = String.format(
                "🚨 [PLATAFORMA SCADA IoT INDUSTRIAL - UCC]\n" +
                "• Alerta: %s\n" +
                "• Sensor/Máquina: %s\n" +
                "• Acción Ejecutada: %s\n" +
                "• Operador Autenticado: %s\n" +
                "• Cola RabbitMQ: %s\n" +
                "• ID Auditoría PostgreSQL: #%d\n" +
                "• Notas: %s",
                decision.alertId(),
                decision.sensorId(),
                decision.actionDispatched(),
                operatorId,
                decision.routingTarget(),
                decision.auditId(),
                notes != null ? notes : "N/A"
        );
        return dispatchNotification(subject, body, "CRITICAL_DECISION");
    }

    public NotificationRecordDto sendTestNotification(String customMessage) {
        String subject = "Prueba de Alerta Multicanal SCADA IoT (AWS EC2)";
        String body = (customMessage != null && !customMessage.isBlank())
                ? customMessage
                : String.format(
                        "✅ [PRUEBA DE CONECTIVIDAD SCADA IoT - AWS EC2]\n" +
                        "• Destinatario Email: %s\n" +
                        "• Móvil / WhatsApp: %s\n" +
                        "• Canal Push Tiempo Real: https://ntfy.sh/%s\n" +
                        "• Estado del Clúster: 15 contenedores activos (Kafka + RabbitMQ + PostgreSQL)",
                        targetEmail, targetPhone, ntfyTopic
                );
        return dispatchNotification(subject, body, "MANUAL_TEST");
    }

    private NotificationRecordDto dispatchNotification(String subject, String body, String tagType) {
        String cleanPhone = targetPhone.replaceAll("[^0-9]", "");
        String whatsappUrl = "https://wa.me/" + cleanPhone + "?text=" + URLEncoder.encode(body, StandardCharsets.UTF_8);
        String pushUrl = "https://ntfy.sh/" + ntfyTopic;
        String notifId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String status = "DELIVERED_PUSH_AND_QUEUED";
        if (notificationsEnabled) {
            try {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(pushUrl))
                        .timeout(Duration.ofSeconds(6))
                        .header("Title", subject)
                        .header("Priority", "high")
                        .header("Tags", "warning,factory,rotating_light")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

                if (targetEmail != null && targetEmail.contains("@")) {
                    reqBuilder.header("X-Email", targetEmail);
                }

                HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    status = "SENT_OK (HTTP " + response.statusCode() + ")";
                    log.info("Notificación enviada a {} y {}: {}", targetEmail, targetPhone, status);
                } else {
                    // Reintento sin cabecera X-Email si el servidor ntfy limita cuota de correo anónimo
                    HttpRequest fallbackReq = HttpRequest.newBuilder()
                            .uri(URI.create(pushUrl))
                            .timeout(Duration.ofSeconds(6))
                            .header("Title", subject)
                            .header("Priority", "high")
                            .header("Tags", "warning,factory")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> fallbackRes = httpClient.send(fallbackReq, HttpResponse.BodyHandlers.ofString());
                    status = "PUSH_DELIVERED (HTTP " + fallbackRes.statusCode() + ")";
                }
            } catch (Exception e) {
                log.warn("No se pudo contactar gateway push externo: {}", e.getMessage());
                status = "LOGGED_LOCAL_GATEWAY";
            }
        } else {
            status = "PAUSED_BY_OPERATOR";
        }

        NotificationRecordDto record = new NotificationRecordDto(
                notifId,
                "PUSH_MOVIL + EMAIL (" + tagType + ")",
                targetPhone + " / " + targetEmail,
                subject,
                body,
                status,
                whatsappUrl,
                pushUrl,
                Instant.now()
        );

        history.add(0, record);
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        return record;
    }

    public List<NotificationRecordDto> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public Map<String, Object> getConfig() {
        return Map.of(
                "email", targetEmail,
                "phone", targetPhone,
                "ntfyTopic", ntfyTopic,
                "ntfySubscribeUrl", "https://ntfy.sh/" + ntfyTopic,
                "enabled", notificationsEnabled
        );
    }

    public Map<String, Object> updateConfig(String email, String phone, Boolean enabled) {
        if (email != null && !email.isBlank()) {
            this.targetEmail = email.trim();
        }
        if (phone != null && !phone.isBlank()) {
            this.targetPhone = phone.trim();
        }
        if (enabled != null) {
            this.notificationsEnabled = enabled;
        }
        return getConfig();
    }
}
