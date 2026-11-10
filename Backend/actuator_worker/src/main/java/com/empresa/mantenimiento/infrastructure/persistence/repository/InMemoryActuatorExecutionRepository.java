package com.empresa.mantenimiento.infrastructure.persistence.repository;

import com.empresa.mantenimiento.domain.model.ActuatorExecutionRecord;
import com.empresa.mantenimiento.domain.model.PhysicalActuator;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.repository.ActuatorExecutionRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryActuatorExecutionRepository implements ActuatorExecutionRepositoryPort {

    private final ConcurrentMap<String, PhysicalActuator> actuators = new ConcurrentHashMap<>();
    private final List<ActuatorExecutionRecord> history = new CopyOnWriteArrayList<>();

    @Override
    public PhysicalActuator getOrCreateActuator(SensorId sensorId) {
        return actuators.computeIfAbsent(sensorId.value(), id -> new PhysicalActuator(sensorId));
    }

    @Override
    public void saveExecutionRecord(ActuatorExecutionRecord record) {
        history.add(0, record);
    }

    @Override
    public List<ActuatorExecutionRecord> findAllHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }
}
