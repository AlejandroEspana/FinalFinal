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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptador RabbitMQ que publica en el Fanout Exchange 'human_alerts' y dispara alerta a Amazon SNS
 * y Push móvil con control de tasa (cooldown de 60s) para no saturar al operador.
 */
@Component
public class RabbitMQHumanAlertPublisherAdapter implements HumanAlertPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQHumanAlertPublisherAdapter.class);
    private static final long NOTIFICATION_COOLDOWN_MS = 60_000L;

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
                    CompletableFuture.runAsync(() -> sendCriticalAlertToSnsAndPush(request));
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error serializando HumanActionRequest a JSON", e);
        }
    }

    private void sendCriticalAlertToSnsAndPush(HumanActionRequest request) {
        String subject = "ALERTA CRITICA SCADA IoT: " + request.sensorId().value() + " (" + request.alertId().value() + ")";
        String msg = String.format(
                "🚨 ALERTA CRÍTICA DETECTADA EN PLANTA INDUSTRIAL (UCC)\n" +
                "• ID Alerta: %s\n" +
                "• Sensor: %s (%.2f mm/s RMS)\n" +
                "• Detalle: %s\n" +
                "• Opciones: %s\n" +
                "• Consola SCADA AWS: http://scada-ucc-alejandro.77.113.90.86.sslip.io:3000",
                request.alertId().value(),
                request.sensorId().value(),
                request.triggerValue(),
                request.description(),
                request.availableOptions()
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

        try {
            HttpRequest pushReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://ntfy.sh/scada-ucc-alejandro-573154613867"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Title", subject)
                    .header("Priority", "urgent")
                    .header("Tags", "rotating_light,warning")
                    .POST(HttpRequest.BodyPublishers.ofString(msg, StandardCharsets.UTF_8))
                    .build();
            httpClient.send(pushReq, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }
}
