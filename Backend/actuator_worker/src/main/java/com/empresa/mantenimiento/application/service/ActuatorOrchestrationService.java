package com.empresa.mantenimiento.application.service;

import com.empresa.mantenimiento.application.dto.ActuatorExecutionDto;
import com.empresa.mantenimiento.application.usecase.ExecuteEmergencyShutdownUseCase;
import com.empresa.mantenimiento.domain.model.ActuatorExecutionRecord;
import com.empresa.mantenimiento.domain.model.PhysicalActuator;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.ShutdownCommand;
import com.empresa.mantenimiento.domain.repository.ActuatorExecutionRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ActuatorOrchestrationService {

    private final ExecuteEmergencyShutdownUseCase executeUseCase;
    private final ActuatorExecutionRepositoryPort repositoryPort;

    public ActuatorOrchestrationService(
            ExecuteEmergencyShutdownUseCase executeUseCase,
            ActuatorExecutionRepositoryPort repositoryPort
    ) {
        this.executeUseCase = Objects.requireNonNull(executeUseCase);
        this.repositoryPort = Objects.requireNonNull(repositoryPort);
    }

    public ActuatorExecutionDto executeShutdown(ShutdownCommand command) {
        return executeUseCase.execute(command);
    }

    public ActuatorExecutionDto resetMachine(String sensorIdStr, String operatorId, String reason) {
        SensorId sensorId = SensorId.of(sensorIdStr);
        PhysicalActuator actuator = repositoryPort.getOrCreateActuator(sensorId);
        ActuatorExecutionRecord record = actuator.reactivarMaquina(operatorId, reason);
        repositoryPort.saveExecutionRecord(record);

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

    public List<ActuatorExecutionDto> getExecutionHistory() {
        return repositoryPort.findAllHistory().stream()
                .map(r -> new ActuatorExecutionDto(
                        r.commandId(),
                        r.sensorId(),
                        r.previousState(),
                        r.currentState(),
                        r.operatorId(),
                        r.reason(),
                        r.executedAt()
                ))
                .sorted(Comparator.comparing(ActuatorExecutionDto::executedAt).reversed())
                .toList();
    }
}
