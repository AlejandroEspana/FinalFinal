package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.service.AlertRouterApplicationService;
import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.presentation.mapper.RouterPresentationMapper;
import com.empresa.mantenimiento.presentation.request.ManualRouteRequest;
import com.empresa.mantenimiento.presentation.response.RoutedAlertResponse;
import com.empresa.mantenimiento.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/router")
@CrossOrigin(origins = "*")
public class RouterMetricsController {

    private final AlertRouterApplicationService routerApplicationService;
    private final RouterPresentationMapper mapper;

    public RouterMetricsController(
            AlertRouterApplicationService routerApplicationService,
            RouterPresentationMapper mapper
    ) {
        this.routerApplicationService = Objects.requireNonNull(routerApplicationService, "routerApplicationService no puede ser nulo");
        this.mapper = Objects.requireNonNull(mapper, "mapper no puede ser nulo");
    }

    @PostMapping("/route")
    public ResponseEntity<ApiResponse<RoutedAlertResponse>> manualRoute(@Valid @RequestBody ManualRouteRequest req) {
        IndustrialAlert alert = new IndustrialAlert(
                AlertId.of(req.alertId()),
                SensorId.of(req.sensorId()),
                AlertSeverity.valueOf(req.severity().toUpperCase()),
                req.triggerValue(),
                req.description(),
                Instant.now()
        );
        HumanActionRequest routed = routerApplicationService.route(alert);
        return ResponseEntity.ok(ApiResponse.success("Alerta enrutada hacia RabbitMQ Fanout Exchange", mapper.toResponse(routed)));
    }
}
