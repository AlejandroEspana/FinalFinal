package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.SensorPlantStatusView;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de Dominio Puro (sin dependencias de Spring) que mantiene la vista materializada de los 10 sensores.
 */
public class PlantMaterializedDomainService {

    private final Map<String, SensorPlantStatusView> views = new ConcurrentHashMap<>();

    public PlantMaterializedDomainService() {
        List.of("sensor_A", "sensor_B", "sensor_C", "sensor_D", "sensor_E",
                "sensor_F", "sensor_G", "sensor_H", "sensor_I", "sensor_J")
                .forEach(id -> views.put(id, new SensorPlantStatusView(SensorId.of(id))));
    }

    public SensorPlantStatusView getOrCreate(String sensorId) {
        return views.computeIfAbsent(sensorId, id -> new SensorPlantStatusView(SensorId.of(id)));
    }

    public Collection<SensorPlantStatusView> getAllViews() {
        return List.copyOf(views.values());
    }
}
