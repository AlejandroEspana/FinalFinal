package com.empresa.mantenimiento.application.service;

import com.empresa.mantenimiento.application.dto.MaintenanceTicketDto;
import com.empresa.mantenimiento.application.usecase.ProcessMaintenanceWorkOrderUseCase;
import com.empresa.mantenimiento.domain.model.MaintenanceWorkOrder;
import com.empresa.mantenimiento.domain.repository.MaintenanceTicketRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class MaintenanceOrchestrationService {

    private final ProcessMaintenanceWorkOrderUseCase processUseCase;
    private final MaintenanceTicketRepositoryPort repositoryPort;

    public MaintenanceOrchestrationService(
            ProcessMaintenanceWorkOrderUseCase processUseCase,
            MaintenanceTicketRepositoryPort repositoryPort
    ) {
        this.processUseCase = Objects.requireNonNull(processUseCase);
        this.repositoryPort = Objects.requireNonNull(repositoryPort);
    }

    public MaintenanceTicketDto processWorkOrder(MaintenanceWorkOrder workOrder) {
        return processUseCase.execute(workOrder);
    }

    public List<MaintenanceTicketDto> getAllTickets() {
        return repositoryPort.findAllTickets().stream()
                .map(t -> new MaintenanceTicketDto(
                        t.getWorkOrder().workOrderId(),
                        t.getWorkOrder().sensorId().value(),
                        t.getWorkOrder().priority(),
                        t.getWorkOrder().delayed(),
                        t.getWorkOrder().delayMillis(),
                        t.getWorkOrder().operatorId(),
                        t.getTicketStatus(),
                        t.getExecutionSummary(),
                        t.getWorkOrder().scheduledAt(),
                        t.getCompletedAt()
                ))
                .toList();
    }
}
