package com.empresa.mantenimiento.infrastructure.persistence.repository;

import com.empresa.mantenimiento.infrastructure.persistence.entity.WindowSnapshotEntity;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryWindowSnapshotRepository {

    private final Map<String, WindowSnapshotEntity> snapshots = new ConcurrentHashMap<>();

    public void save(WindowSnapshotEntity entity) {
        snapshots.put(entity.sensorId(), entity);
    }

    public Collection<WindowSnapshotEntity> findAll() {
        return snapshots.values();
    }
}
