package com.empresa.mantenimiento.infrastructure.persistence.mapper;

import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.empresa.mantenimiento.infrastructure.persistence.entity.SimulationLogEntity;
import org.springframework.stereotype.Component;

@Component
public class TelemetryPersistenceMapper {

    public SimulationLogEntity toEntity(VibrationReadingRecord record) {
        return new SimulationLogEntity(
                record.sensorId().value(),
                record.vibrationValue(),
                record.timestamp()
        );
    }
}
