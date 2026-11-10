package com.empresa.mantenimiento.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Entidad de Dominio Rico que modela el ciclo de vida de una alerta interactiva en consola.
 */
public class InteractiveAlertSession {

    private final HumanActionRequest request;
    private boolean resolved;
    private Instant resolvedAt;

    public InteractiveAlertSession(HumanActionRequest request) {
        this.request = Objects.requireNonNull(request, "request no puede ser nulo");
        this.resolved = false;
    }

    public synchronized void marcarComoResuelta() {
        this.resolved = true;
        this.resolvedAt = Instant.now();
    }

    public HumanActionRequest getRequest() {
        return request;
    }

    public synchronized boolean isResolved() {
        return resolved;
    }

    public synchronized Instant getResolvedAt() {
        return resolvedAt;
    }
}
