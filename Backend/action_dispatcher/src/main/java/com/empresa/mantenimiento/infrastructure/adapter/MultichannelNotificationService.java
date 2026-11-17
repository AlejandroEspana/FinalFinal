package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.application.dto.DecisionResultDto;
import com.empresa.mantenimiento.application.dto.NotificationRecordDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Adaptador Multicanal de Notificaciones Industriales:
 * 1) Amazon SNS Oficial (arn:aws:sns:us-east-2:148395526595:scada-iot-alertas-ucc) -> Email + SMS
 * 2) Push Móvil en Tiempo Real (ntfy.sh)
 * 3) Enlace Directo a WhatsApp (+573154613867)
 */
@Service
public class MultichannelNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MultichannelNotificationService.class);
    private static final int MAX_HISTORY = 50;

    private volatile String targetEmail;
    private volatile String targetPhone;
    private volatile String ntfyTopic;
    private volatile String snsBridgeUrl;
    private volatile boolean notificationsEnabled;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final List<NotificationRecordDto> history = new CopyOnWriteArrayList<>();

    public MultichannelNotificationService(
            @Value("${app.notifications.email:alejandro.espaname@campusucc.edu.co}") String targetEmail,
            @Value("${app.notifications.phone:+573154613867}") String targetPhone,
            @Value("${app.notifications.ntfy-topic:scada-ucc-alejandro-573154613867}") String ntfyTopic,
            @Value("${app.notifications.sns-bridge-url:http://host.docker.internal:9099/publish}") String snsBridgeUrl,
            @Value("${app.notifications.enabled:true}") boolean notificationsEnabled
    ) {
        this.targetEmail = targetEmail;
        this.targetPhone = targetPhone;
        this.ntfyTopic = ntfyTopic;
        this.snsBridgeUrl = snsBridgeUrl;
        this.notificationsEnabled = notificationsEnabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public NotificationRecordDto notifyOperatorDecision(DecisionResultDto decision, String operatorId, String notes) {
        String subject = String.format("SCADA IoT [%s] -> %s (%s)",
                decision.actionDispatched(), decision.sensorId(), decision.alertId());
        String body = String.format(
                "🚨 [PLATAFORMA SCADA IoT INDUSTRIAL - UCC]\n" +
                "• Alerta: %s\n" +
                "• Sensor/Máquina: %s\n" +
                "• Acción Ejecutada: %s\n" +
                "• Operador Autenticado: %s\n" +
                "• Cola RabbitMQ: %s\n" +
                "• ID Auditoría PostgreSQL: #%d\n" +
                "• Notas: %s\n" +
                "• Consola AWS: http://scada-ucc-alejandro.77.113.90.86.sslip.io:3000",
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
        String subject = "SCADA IoT UCC - Alerta Amazon SNS + Push Movil Activa";
        String body = (customMessage != null && !customMessage.isBlank())
                ? customMessage
                : String.format(
                        "✅ [PRUEBA OFICIAL AMAZON SNS + PUSH SCADA IoT - AWS EC2]\n" +
                        "• Tópico AWS SNS: arn:aws:sns:us-east-2:148395526595:scada-iot-alertas-ucc\n" +
                        "• Destinatario Email SNS: %s\n" +
                        "• Móvil / SMS / WhatsApp: %s\n" +
                        "• Canal Push Tiempo Real: https://ntfy.sh/%s\n" +
                        "• Dominio SCADA: http://scada-ucc-alejandro.77.113.90.86.sslip.io:3000",
                        targetEmail, targetPhone, ntfyTopic
                );
        return dispatchNotification(subject, body, "AWS_SNS_TEST");
    }

    private NotificationRecordDto dispatchNotification(String subject, String body, String tagType) {
        String cleanPhone = targetPhone.replaceAll("[^0-9]", "");
        String whatsappUrl = "https://wa.me/" + cleanPhone + "?text=" + URLEncoder.encode(body, StandardCharsets.UTF_8);
        String pushUrl = "https://ntfy.sh/" + ntfyTopic;
        String notifId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        List<String> statuses = new ArrayList<>();

        if (notificationsEnabled) {
            // 1. Publicar en Amazon SNS oficial a través del puente systemd con rol IAM ec2-scada-sns-role
            try {
                String jsonPayload = objectMapper.writeValueAsString(Map.of(
                        "subject", subject,
                        "message", body
                ));
                HttpRequest snsReq = HttpRequest.newBuilder()
                        .uri(URI.create(snsBridgeUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> snsRes = httpClient.send(snsReq, HttpResponse.BodyHandlers.ofString());
                if (snsRes.statusCode() >= 200 && snsRes.statusCode() < 300) {
                    JsonNode node = objectMapper.readTree(snsRes.body());
                    String msgId = node.path("messageId").asText("OK");
                    statuses.add("AWS_SNS_OK(" + msgId.substring(0, Math.min(8, msgId.length())) + ")");
                }
            } catch (Exception e) {
                log.debug("Puente AWS SNS local no disponible en este entorno: {}", e.getMessage());
            }

            // 2. Publicar en canal Push móvil en tiempo real (ntfy.sh)
            try {
                HttpRequest pushReq = HttpRequest.newBuilder()
                        .uri(URI.create(pushUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("Title", subject)
                        .header("Priority", "high")
                        .header("Tags", "warning,factory")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> pushRes = httpClient.send(pushReq, HttpResponse.BodyHandlers.ofString());
                if (pushRes.statusCode() >= 200 && pushRes.statusCode() < 300) {
                    statuses.add("PUSH_OK");
                }
            } catch (Exception e) {
                log.warn("No se pudo contactar ntfy.sh: {}", e.getMessage());
            }
        } else {
            statuses.add("PAUSED");
        }

        String finalStatus = statuses.isEmpty() ? "QUEUED_LOCAL" : String.join(" + ", statuses);

        NotificationRecordDto record = new NotificationRecordDto(
                notifId,
                "AWS_SNS + PUSH + WHATSAPP (" + tagType + ")",
                targetPhone + " / " + targetEmail,
                subject,
                body,
                finalStatus,
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
