package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.domain.repository.HumanAlertPublisherPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RabbitMQHumanAlertPublisherAdapter implements HumanAlertPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQHumanAlertPublisherAdapter.class);
    private static final long NOTIFICATION_COOLDOWN_MS = 60_000L;
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

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String fanoutExchangeName;
    private final HttpClient httpClient;
    private final AtomicLong lastExternalNotificationAt = new AtomicLong(0L);

    public RabbitMQHumanAlertPublisherAdapter(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${app.rabbitmq.exchanges.human-alerts:human_alerts}") String fanoutExchangeName
    ) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.fanoutExchangeName = Objects.requireNonNull(fanoutExchangeName, "fanoutExchangeName no puede ser nulo");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    }

    @Override
    public void publish(HumanActionRequest request) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        try {
            String payload = objectMapper.writeValueAsString(request);
            rabbitTemplate.convertAndSend(fanoutExchangeName, "", payload);
            log.info("Solicitud humana publicada en Fanout Exchange [{}] -> Alerta: {}, Opciones: {}",
                    fanoutExchangeName, request.alertId(), request.availableOptions());

            if (request.severity() == AlertSeverity.CRITICAL) {
                long now = System.currentTimeMillis();
                long prev = lastExternalNotificationAt.get();
                if (now - prev >= NOTIFICATION_COOLDOWN_MS && lastExternalNotificationAt.compareAndSet(prev, now)) {
                    CompletableFuture.runAsync(() -> sendCriticalAlertToSns(request));
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error serializando HumanActionRequest a JSON", e);
        }
    }

    private void sendCriticalAlertToSns(HumanActionRequest request) {
        String sensorId = request.sensorId().value();
        String location = SENSOR_BUILDING_LOCATION.getOrDefault(sensorId, "Edificio Industrial — Sector " + sensorId);
        String formattedDate = COLOMBIA_TIME_FMT.format(Instant.now());

        String subject = String.format("[ALERTA CRÍTICA ISO-10816] %s (%.2f mm/s) — Acción Requerida",
                sensorId, request.triggerValue());

        String msg = String.format(
                "======================================================================\n" +
                "ALERTA TEMPRANA DE ANOMALÍA MECÁNICA — CENTRO DE MONITOREO SCADA IoT\n" +
                "CLASIFICACIÓN DE SEVERIDAD: ZONA D (PELIGRO DE DAÑO ESTRUCTURAL)\n" +
                "======================================================================\n\n" +
                "ATENCIÓN INGENIERO DE TURNO:\n\n" +
                "El motor analítico Stateful (Apache Kafka) ha detectado una violación\n" +
                "del umbral crítico de vibración RMS (> 90.0 mm/s) que requiere una\n" +
                "decisión humana inmediata en la consola interactiva:\n\n" +
                "1. DIAGNÓSTICO DEL ACTIVO AFECTADO\n" +
                "   • ID de Alerta             : %s\n" +
                "   • Sensor / Máquina         : %s\n" +
                "   • Ubicación en Edificio 3D : %s\n" +
                "   • Lectura RMS Registrada   : %.2f mm/s (Umbral máximo: 90.0 mm/s)\n" +
                "   • Diagnóstico Automático   : %s\n" +
                "   • Marca Temporal           : %s\n\n" +
                "2. PROTOCOLOS DE MITIGACIÓN HABILITADOS EN CONSOLA\n" +
                "   • Opciones Disponibles     : %s\n\n" +
                "----------------------------------------------------------------------\n" +
                "INGRESE AL GEMELO DIGITAL 3D PARA AUTORIZAR LA MANIOBRA (AWS CLOUDFRONT):\n" +
                "https://dnzhr4v1oxvn7.cloudfront.net\n" +
                "----------------------------------------------------------------------\n",
                request.alertId().value(),
                sensorId,
                location,
                request.triggerValue(),
                request.description(),
                formattedDate,
                String.join("  |  ", request.availableOptions())
        );
        try {
            String snsJson = objectMapper.writeValueAsString(Map.of("subject", subject, "message", msg));
            HttpRequest snsReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://host.docker.internal:9099/publish"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(snsJson, StandardCharsets.UTF_8))
                    .build();
            httpClient.send(snsReq, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }
}
