package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.domain.model.AlertSeverity;

import java.util.List;
import java.util.Objects;

public class DecisionOptionsPolicyDomainService {

    public static final String ACTION_SHUTDOWN_NOW = "APAGADO_INMEDIATO";
    public static final String ACTION_IGNORE_10MIN = "IGNORAR_10_MINUTOS";
    public static final String ACTION_MAINTENANCE_NOW = "PROGRAMAR_MANTENIMIENTO_AHORA";
    public static final String ACTION_ACK_WAIT_24H = "RECONOCER_Y_ESPERAR_24H";

    private static final List<String> CRITICAL_OPTIONS = List.of(ACTION_SHUTDOWN_NOW, ACTION_IGNORE_10MIN);
    private static final List<String> WARNING_OPTIONS = List.of(ACTION_MAINTENANCE_NOW, ACTION_ACK_WAIT_24H);

    public List<String> resolveOptionsFor(AlertSeverity severity) {
        Objects.requireNonNull(severity, "La severidad no puede ser nula.");
        return switch (severity) {
            case CRITICAL -> CRITICAL_OPTIONS;
            case WARNING -> WARNING_OPTIONS;
            case NORMAL -> List.of("NINGUNA_ACCION_REQUERIDA");
        };
    }
}
