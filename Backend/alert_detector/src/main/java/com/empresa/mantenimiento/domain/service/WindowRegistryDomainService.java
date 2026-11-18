package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.SensorVibrationWindow;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WindowRegistryDomainService {

    private final ConcurrentMap<SensorId, SensorVibrationWindow> windows = new ConcurrentHashMap<>();

    public SensorVibrationWindow getOrCreateWindow(SensorId sensorId) {
        return windows.computeIfAbsent(sensorId, id -> new SensorVibrationWindow(id, SensorVibrationWindow.DEFAULT_WINDOW_CAPACITY));
    }

    public Collection<SensorVibrationWindow> getAllWindows() {
        return List.copyOf(windows.values());
    }

    public void resetWindow(SensorId sensorId) {
        SensorVibrationWindow window = windows.get(sensorId);
        if (window != null) {
            window.resetear();
        }
    }
}
