package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.application.dto.MaintenanceTicketDto;
import com.empresa.mantenimiento.domain.model.MaintenanceExecutionTicket;
import com.empresa.mantenimiento.domain.model.MaintenanceWorkOrder;
import com.empresa.mantenimiento.domain.repository.MaintenanceTicketRepositoryPort;
import com.empresa.mantenimiento.domain.service.MaintenanceSchedulingDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ProcessMaintenanceWorkOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessMaintenanceWorkOrderUseCase.class);

    private final MaintenanceTicketRepositoryPort repositoryPort;
    private final MaintenanceSchedulingDomainService schedulingDomainService;

    public ProcessMaintenanceWorkOrderUseCase(
            MaintenanceTicketRepositoryPort repositoryPort,
            MaintenanceSchedulingDomainService schedulingDomainService
    ) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort no puede ser nulo");
        this.schedulingDomainService = Objects.requireNonNull(schedulingDomainService, "schedulingDomainService no puede ser nulo");
    }

    public MaintenanceTicketDto execute(MaintenanceWorkOrder workOrder) {
        Objects.requireNonNull(workOrder, "workOrder no puede ser nulo");

        MaintenanceExecutionTicket ticket = new MaintenanceExecutionTicket(workOrder);
        ticket.completarOrdenTecnica();
        repositoryPort.saveTicket(ticket);

        log.info(schedulingDomainService.formatMaintenanceBanner(ticket));

        return new MaintenanceTicketDto(
                workOrder.workOrderId(),
                workOrder.sensorId().value(),
                workOrder.priority(),
                workOrder.delayed(),
                workOrder.delayMillis(),
                workOrder.operatorId(),
                ticket.getTicketStatus(),
                ticket.getExecutionSummary(),
                workOrder.scheduledAt(),
                ticket.getCompletedAt()
        );
    }
}
