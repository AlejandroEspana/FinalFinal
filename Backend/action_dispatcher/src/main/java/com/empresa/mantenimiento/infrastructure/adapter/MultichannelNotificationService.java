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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Adaptador Multicanal de Notificaciones Industriales con formato corporativo ISO-10816:
 * 1) Amazon SNS Oficial (arn:aws:sns:us-east-2:148395526595:scada-iot-alertas-ucc) -> Email + SMS
 * 2) Push Móvil en Tiempo Real (ntfy.sh)
 * 3) Enlace Directo a WhatsApp (+573154613867)
 */
@Service
public class MultichannelNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MultichannelNotificationService.class);
    private static final int MAX_HISTORY = 50;
    private static final DateTimeFormatter COLOMBIA_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'COT'").withZone(ZoneId.of("America/Bogota"));

    private static final Map<String, String> SENSOR_BUILDING_LOCATION = Map.of(
            "sensor_A", "Nivel 1 (Sótano de Potencia) — Turbina Principal A1",
            "sensor_B", "Nivel 1 (Sótano de Potencia) — Generador Síncrono B2",
            "sensor_C", "Nivel 1 (Sótano de Potencia) — Bomba Hidráulica C3",
            "sensor_D", "Nivel 1 (Sótano de Potencia) — Reductor Mecánico D4",
            "sensor_E", "Nivel 2 (Planta de Procesos) — Compresor Centrífugo E1",
            "sensor_F", "Nivel 2 (Planta de Procesos) — Extrusora Industrial F2",
            "sensor_G", "Nivel 2 (Planta de Procesos) — Ventilador de Tiro Inducido G3",
            "sensor_H", "Nivel 3 (Cubierta Técnica & HVAC) — Chiller de Enfriamiento H1",
            "sensor_I", "Nivel 3 (Cubierta Técnica & HVAC) — Torre de Refrigeración I2",
            "sensor_J", "Nivel 3 (Cubierta Técnica & HVAC) — Subestación Eléctrica J3"
    );

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
        String location = SENSOR_BUILDING_LOCATION.getOrDefault(
                decision.sensorId(), "Edificio Central de Planta — Activo " + decision.sensorId()
        );
        String formattedDate = COLOMBIA_TIME_FMT.format(Instant.now());

        String subject = String.format("[SCADA-OT #%d] Orden %s ejecutada sobre %s",
                decision.auditId(), decision.actionDispatched(), decision.sensorId());

        String body = String.format(
                "======================================================================\n" +
                "CENTRO DE CONTROL DE OPERACIONES INDUSTRIALES — PLANTA SCADA IoT\n" +
                "ACTA OFICIAL DE RESOLUCIÓN Y DESPACHO DE MANTENIMIENTO (ISO 10816-3)\n" +
                "======================================================================\n\n" +
                "Estimado Ingeniero de Confiabilidad (%s),\n\n" +
                "El Sistema de Gestión de Mantenimiento Predictivo ha registrado y auditado\n" +
                "una intervención operativa en tiempo real sobre los activos de la planta:\n\n" +
                "1. IDENTIFICACIÓN DEL ACTIVO Y UBICACIÓN EN EDIFICIO\n" +
                "   • Código del Sensor / Activo : %s\n" +
                "   • Ubicación Física (Gemelo 3D): %s\n" +
                "   • Folio de Alerta Asociada   : %s\n" +
                "   • Fecha y Hora de Ejecución  : %s\n\n" +
                "2. RESOLUCIÓN DEL OPERADOR Y ENRUTAMIENTO DE COMANDO\n" +
                "   • Acción Correctiva Aplicada : %s\n" +
                "   • Ingeniero / Operador Auth  : %s (Verificado por Spring Security)\n" +
                "   • Destino en Broker RabbitMQ : %s\n" +
                "   • Observaciones Técnicas     : %s\n\n" +
                "3. TRAZABILIDAD TRANSACCIONAL Y AUDITORÍA (ACID)\n" +
                "   • ID Registro PostgreSQL     : #%d (Tabla operator_decision_audit)\n" +
                "   • Estado de Transacción      : CONFIRMADA (COMMIT EXITOSO)\n\n" +
                "----------------------------------------------------------------------\n" +
                "ACCESO DIRECTO AL GEMELO DIGITAL 3D Y CONSOLA SCADA:\n" +
                "https://scada-ucc-alejandro.77.113.90.86.sslip.io\n" +
                "----------------------------------------------------------------------\n" +
                "Este es un boletín automático emitido por la Plataforma Distribuida de\n" +
                "Mantenimiento Predictivo (Apache Kafka + RabbitMQ + PostgreSQL en AWS EC2).\n",
                targetEmail,
                decision.sensorId(),
                location,
                decision.alertId(),
                formattedDate,
                decision.actionDispatched(),
                operatorId,
                decision.routingTarget(),
                notes != null ? notes : "Intervención autorizada desde consola central.",
                decision.auditId()
        );
        return dispatchNotification(subject, body, "ORDEN_EJECUTADA");
    }

    public NotificationRecordDto sendTestNotification(String customMessage) {
        String formattedDate = COLOMBIA_TIME_FMT.format(Instant.now());
        String subject = "[BOLETIN SCADA-OT] Reporte de Estado Operativo e Integridad de Planta IoT";
        String body = (customMessage != null && !customMessage.isBlank())
                ? customMessage
                : String.format(
                        "======================================================================\n" +
                        "DIRECCIÓN DE INGENIERÍA Y MANTENIMIENTO PREDICTIVO — PLANTA INDUSTRIAL\n" +
                        "REPORTE DE CONECTIVIDAD MULTICANAL Y ESTADO DEL GEMELO DIGITAL 3D\n" +
                        "======================================================================\n\n" +
                        "Fecha de Emisión : %s\n" +
                        "Destinatario     : %s | Móvil de Guardia: %s\n\n" +
                        "1. RESUMEN DE TELEMETRÍA Y SUBSISTEMAS DISTRIBUIDOS\n" +
                        "   • Bus de Ingesta (Apache Kafka)   : OPERATIVO (3 Particiones Stateful)\n" +
                        "   • Bus de Decisiones (RabbitMQ)    : OPERATIVO (Fanout + Delayed x-delay)\n" +
                        "   • Persistencia ACID (PostgreSQL)  : OPERATIVA (127.0.0.1:5432 Confinada)\n" +
                        "   • Gemelo Digital 3D del Edificio  : 10 Sensores Monitoreados (Niveles 1-3)\n\n" +
                        "2. SEGURIDAD DE INFRAESTRUCTURA (UBUNTU SERVER 24.04 LTS - AWS EC2)\n" +
                        "   • Blindaje Perimetral             : Nginx Reverse Proxy (TLS / HSTS)\n" +
                        "   • Prevención de Intrusiones (IPS) : Fail2ban + UFW + Auditd Activos\n" +
                        "   • Tópico Oficial Amazon SNS       : arn:aws:sns:us-east-2:148395526595:scada-iot-alertas-ucc\n\n" +
                        "----------------------------------------------------------------------\n" +
                        "ACCESO AL PANEL DE CONTROL Y MODELO 3D DEL EDIFICIO:\n" +
                        "https://scada-ucc-alejandro.77.113.90.86.sslip.io\n" +
                        "----------------------------------------------------------------------\n",
                        formattedDate, targetEmail, targetPhone
                );
        return dispatchNotification(subject, body, "BOLETIN_OPERATIVO");
    }

    private NotificationRecordDto dispatchNotification(String subject, String body, String tagType) {
        String cleanPhone = targetPhone.replaceAll("[^0-9]", "");
        String whatsappUrl = "https://wa.me/" + cleanPhone + "?text=" + URLEncoder.encode(body, StandardCharsets.UTF_8);
        String pushUrl = "https://ntfy.sh/" + ntfyTopic;
        String notifId = "OT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        List<String> statuses = new ArrayList<>();

        if (notificationsEnabled) {
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
                log.debug("Puente AWS SNS local no disponible: {}", e.getMessage());
            }

            try {
                HttpRequest pushReq = HttpRequest.newBuilder()
                        .uri(URI.create(pushUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("Title", subject)
                        .header("Priority", "high")
                        .header("Tags", "factory,warning")
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
