package com.empresa.mantenimiento.infrastructure.persistence.repository;

import com.empresa.mantenimiento.infrastructure.persistence.entity.OperatorDecisionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataDecisionAuditRepository extends JpaRepository<OperatorDecisionAuditEntity, Long> {

    List<OperatorDecisionAuditEntity> findAllByOrderByRecordedAtDesc();

    List<OperatorDecisionAuditEntity> findBySensorIdOrderByRecordedAtDesc(String sensorId);
}
