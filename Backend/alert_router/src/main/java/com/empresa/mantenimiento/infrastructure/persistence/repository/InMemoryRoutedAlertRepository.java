package com.empresa.mantenimiento.infrastructure.persistence.repository;

import com.empresa.mantenimiento.infrastructure.persistence.entity.RoutedAlertHistoryEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryRoutedAlertRepository {

    private final List<RoutedAlertHistoryEntity> history = new CopyOnWriteArrayList<>();

    public void save(RoutedAlertHistoryEntity entity) {
        if (history.size() >= 100) {
            history.remove(0);
        }
        history.add(entity);
    }

    public List<RoutedAlertHistoryEntity> findAll() {
        return List.copyOf(history);
    }
}
