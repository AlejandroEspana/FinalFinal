package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;

public interface TelemetryEventPublisherPort {

    void publish(VibrationReadingRecord reading);
}
