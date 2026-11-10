package com.empresa.mantenimiento.infrastructure.persistence.mapper;

import com.empresa.mantenimiento.domain.model.SensorVibrationWindow;
import com.empresa.mantenimiento.infrastructure.persistence.entity.WindowSnapshotEntity;
import org.springframework.stereotype.Component;

@Component
public class WindowSnapshotMapper {

    public WindowSnapshotEntity toEntity(SensorVibrationWindow window) {
        return new WindowSnapshotEntity(
                window.getSensorId().value(),
                window.getSeveridadActual().name(),
                window.getCantidadLecturas(),
                window.getPromedioVibracion(),
                window.getLastEvaluationAt()
        );
    }
}
