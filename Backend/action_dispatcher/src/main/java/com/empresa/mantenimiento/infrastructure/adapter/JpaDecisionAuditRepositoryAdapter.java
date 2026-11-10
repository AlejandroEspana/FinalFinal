package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.application.dto.AuditRecordDto;
import com.empresa.mantenimiento.domain.model.MaintenanceDecision;
import com.empresa.mantenimiento.domain.repository.DecisionAuditRepositoryPort;
import com.empresa.mantenimiento.infrastructure.persistence.entity.OperatorDecisionAuditEntity;
import com.empresa.mantenimiento.infrastructure.persistence.mapper.DecisionAuditPersistenceMapper;
import com.empresa.mantenimiento.infrastructure.persistence.repository.SpringDataDecisionAuditRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Adaptador de Infraestructura JPA que implementa el puerto de auditoría en PostgreSQL.
 */
@Component
public class JpaDecisionAuditRepositoryAdapter implements DecisionAuditRepositoryPort {

    private final SpringDataDecisionAuditRepository jpaRepository;
    private final DecisionAuditPersistenceMapper mapper;

    public JpaDecisionAuditRepositoryAdapter(
            SpringDataDecisionAuditRepository jpaRepository,
            DecisionAuditPersistenceMapper mapper
    ) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository no puede ser nulo");
        this.mapper = Objects.requireNonNull(mapper, "mapper no puede ser nulo");
    }

    @Override
    public Long recordAudit(MaintenanceDecision decision) {
        OperatorDecisionAuditEntity entity = mapper.toEntity(decision);
        OperatorDecisionAuditEntity saved = jpaRepository.save(entity);
        return saved.getId() != null ? saved.getId() : 1L;
    }

    @Override
    public List<AuditRecordDto> findAllOrdered() {
        return jpaRepository.findAllByOrderByRecordedAtDesc()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<AuditRecordDto> findBySensorId(String sensorId) {
        return jpaRepository.findBySensorIdOrderByRecordedAtDesc(sensorId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
