package com.empresa.mantenimiento.presentation.mapper;

import com.empresa.mantenimiento.domain.model.SensorVibrationWindow;
import com.empresa.mantenimiento.presentation.response.WindowStatusResponse;
import org.springframework.stereotype.Component;

@Component
public class WindowPresentationMapper {

    public WindowStatusResponse toResponse(SensorVibrationWindow window) {
        return new WindowStatusResponse(
                window.getSensorId().value(),
                window.getSeveridadActual().name(),
                window.getCantidadLecturas(),
                window.getPromedioVibracion(),
                window.getLastEvaluationAt()
        );
    }
}
