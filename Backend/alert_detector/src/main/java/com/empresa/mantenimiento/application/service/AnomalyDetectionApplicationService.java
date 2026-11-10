package com.empresa.mantenimiento.application.service;

import com.empresa.mantenimiento.application.usecase.EvaluateSensorTelemetryUseCase;
import com.empresa.mantenimiento.domain.model.IndustrialAlert;
import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class AnomalyDetectionApplicationService {

    private final EvaluateSensorTelemetryUseCase evaluateUseCase;

    public AnomalyDetectionApplicationService(EvaluateSensorTelemetryUseCase evaluateUseCase) {
        this.evaluateUseCase = Objects.requireNonNull(evaluateUseCase, "evaluateUseCase no puede ser nulo");
    }

    public Optional<IndustrialAlert> handleIncomingReading(VibrationReadingRecord reading) {
        return evaluateUseCase.execute(reading);
    }
}
