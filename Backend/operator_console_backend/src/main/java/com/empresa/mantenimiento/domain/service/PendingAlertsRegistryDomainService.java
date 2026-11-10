package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.domain.model.InteractiveAlertSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingAlertsRegistryDomainService {

    private final Map<String, InteractiveAlertSession> sessions = new ConcurrentHashMap<>();

    public InteractiveAlertSession register(HumanActionRequest request) {
        InteractiveAlertSession session = new InteractiveAlertSession(request);
        sessions.put(request.alertId().value(), session);
        return session;
    }

    public void resolve(String alertId) {
        InteractiveAlertSession session = sessions.remove(alertId);
        if (session != null) {
            session.marcarComoResuelta();
        }
    }

    public List<HumanActionRequest> getPendingRequests() {
        return sessions.values().stream()
                .filter(s -> !s.isResolved())
                .map(InteractiveAlertSession::getRequest)
                .toList();
    }
}
