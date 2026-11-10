package com.empresa.mantenimiento.application.service;

import com.empresa.mantenimiento.application.dto.ActuatorExecutionDto;
import com.empresa.mantenimiento.application.usecase.ExecuteEmergencyShutdownUseCase;
import com.empresa.mantenimiento.domain.model.ShutdownCommand;
import com.empresa.mantenimiento.domain.repository.ActuatorExecutionRepositoryPort;
import org.springframework.stereotype.Service;

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
                .toList();
    }
}
