package com.empresa.mantenimiento.infrastructure.persistence.repository;

import com.empresa.mantenimiento.infrastructure.persistence.entity.OperatorSessionEntity;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOperatorSessionRepository {

    private final Map<String, OperatorSessionEntity> store = new ConcurrentHashMap<>();

    public void save(OperatorSessionEntity entity) {
        store.put(entity.alertId(), entity);
    }
}
