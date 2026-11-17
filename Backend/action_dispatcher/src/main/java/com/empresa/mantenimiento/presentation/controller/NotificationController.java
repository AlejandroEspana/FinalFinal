package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.dto.NotificationRecordDto;
import com.empresa.mantenimiento.infrastructure.adapter.MultichannelNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final MultichannelNotificationService notificationService;

    public NotificationController(MultichannelNotificationService notificationService) {
        this.notificationService = Objects.requireNonNull(notificationService);
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(notificationService.getConfig());
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        String email = body.get("email") instanceof String s ? s : null;
        String phone = body.get("phone") instanceof String s ? s : null;
        Boolean enabled = body.get("enabled") instanceof Boolean b ? b : null;
        return ResponseEntity.ok(notificationService.updateConfig(email, phone, enabled));
    }

    @PostMapping("/test")
    public ResponseEntity<NotificationRecordDto> sendTest(@RequestBody(required = false) Map<String, String> body) {
        String customMsg = body != null ? body.get("message") : null;
        return ResponseEntity.ok(notificationService.sendTestNotification(customMsg));
    }

    @GetMapping("/history")
    public ResponseEntity<List<NotificationRecordDto>> getHistory() {
        return ResponseEntity.ok(notificationService.getHistory());
    }
}
