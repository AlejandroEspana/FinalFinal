package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.application.dto.ActuatorExecutionDto;
import com.empresa.mantenimiento.domain.model.ActuatorExecutionRecord;
import com.empresa.mantenimiento.domain.model.PhysicalActuator;
import com.empresa.mantenimiento.domain.model.ShutdownCommand;
import com.empresa.mantenimiento.domain.repository.ActuatorExecutionRepositoryPort;
import com.empresa.mantenimiento.domain.service.EmergencySafetyDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ExecuteEmergencyShutdownUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExecuteEmergencyShutdownUseCase.class);

    private final ActuatorExecutionRepositoryPort repositoryPort;
    private final EmergencySafetyDomainService safetyDomainService;

    public ExecuteEmergencyShutdownUseCase(
            ActuatorExecutionRepositoryPort repositoryPort,
            EmergencySafetyDomainService safetyDomainService
    ) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort no puede ser nulo");
        this.safetyDomainService = Objects.requireNonNull(safetyDomainService, "safetyDomainService no puede ser nulo");
    }

    public ActuatorExecutionDto execute(ShutdownCommand command) {
        Objects.requireNonNull(command, "command no puede ser nulo");

        PhysicalActuator actuator = repositoryPort.getOrCreateActuator(command.sensorId());
        ActuatorExecutionRecord record = actuator.ejecutarCorteEnergiaInmediato(command);
        repositoryPort.saveExecutionRecord(record);

        String banner = safetyDomainService.formatPhysicalConsoleBanner(record);
        log.warn(banner);

        return new ActuatorExecutionDto(
                record.commandId(),
                record.sensorId(),
                record.previousState(),
                record.currentState(),
                record.operatorId(),
                record.reason(),
                record.executedAt()
        );
    }
}
