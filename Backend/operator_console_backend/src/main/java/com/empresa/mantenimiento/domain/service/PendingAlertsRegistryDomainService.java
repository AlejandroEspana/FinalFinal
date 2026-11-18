package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.domain.model.InteractiveAlertSession;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PendingAlertsRegistryDomainService {

    public static final int MAX_PENDING_SESSIONS = 100;

    private final Map<String, InteractiveAlertSession> sessions = new ConcurrentHashMap<>();

    public synchronized InteractiveAlertSession register(HumanActionRequest request) {
        if (sessions.size() >= MAX_PENDING_SESSIONS) {
            sessions.values().stream()
                    .min(Comparator.comparing(s -> s.getRequest().timestamp()))
                    .ifPresent(oldest -> sessions.remove(oldest.getRequest().alertId().value()));
        }
        InteractiveAlertSession session = new InteractiveAlertSession(request);
        sessions.put(request.alertId().value(), session);
        return session;
    }

    public synchronized void resolve(String alertId) {
        InteractiveAlertSession session = sessions.remove(alertId);
        if (session != null) {
            session.marcarComoResuelta();
        }
    }

    public List<HumanActionRequest> getPendingRequests() {
        return sessions.values().stream()
                .filter(s -> !s.isResolved())
                .map(InteractiveAlertSession::getRequest)
                .sorted(Comparator.comparing(HumanActionRequest::timestamp).reversed())
                .toList();
    }
}
