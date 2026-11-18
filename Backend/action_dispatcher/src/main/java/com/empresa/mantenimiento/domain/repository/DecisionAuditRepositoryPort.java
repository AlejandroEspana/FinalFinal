package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.application.dto.AuditRecordDto;
import com.empresa.mantenimiento.domain.model.MaintenanceDecision;

import java.util.List;

public interface DecisionAuditRepositoryPort {

    Long recordAudit(MaintenanceDecision decision);

    List<AuditRecordDto> findAllOrdered();

    List<AuditRecordDto> findBySensorId(String sensorId);
}
