package com.empresa.mantenimiento.infrastructure.persistence.repository;

import com.empresa.mantenimiento.infrastructure.persistence.entity.PlantSensorStateEntity;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPlantStateRepository {

    private final Map<String, PlantSensorStateEntity> store = new ConcurrentHashMap<>();

    public void save(PlantSensorStateEntity entity) {
        store.put(entity.sensorId(), entity);
    }
}
