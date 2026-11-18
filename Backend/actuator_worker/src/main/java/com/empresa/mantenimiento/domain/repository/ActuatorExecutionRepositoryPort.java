package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.ActuatorExecutionRecord;
import com.empresa.mantenimiento.domain.model.PhysicalActuator;
import com.empresa.mantenimiento.domain.model.SensorId;

import java.util.List;

public interface ActuatorExecutionRepositoryPort {

    PhysicalActuator getOrCreateActuator(SensorId sensorId);

    void saveExecutionRecord(ActuatorExecutionRecord record);

    List<ActuatorExecutionRecord> findAllHistory();
}
