package com.empresa.mantenimiento.infrastructure.persistence.repository;

import com.empresa.mantenimiento.infrastructure.persistence.entity.SimulationLogEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemorySimulationLogRepository {

    private final List<SimulationLogEntity> logs = new CopyOnWriteArrayList<>();

    public void save(SimulationLogEntity entity) {
        if (logs.size() >= 100) {
            logs.remove(0);
        }
        logs.add(entity);
    }

    public List<SimulationLogEntity> findAll() {
        return List.copyOf(logs);
    }
}
