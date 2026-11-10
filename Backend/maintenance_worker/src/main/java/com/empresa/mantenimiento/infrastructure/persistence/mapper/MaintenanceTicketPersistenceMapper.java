package com.empresa.mantenimiento.infrastructure.persistence.mapper;

import com.empresa.mantenimiento.domain.model.MaintenanceExecutionTicket;
import com.empresa.mantenimiento.infrastructure.persistence.entity.MaintenanceTicketEntity;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceTicketPersistenceMapper {

    public MaintenanceTicketEntity toEntity(MaintenanceExecutionTicket ticket) {
        return new MaintenanceTicketEntity(
                ticket.getWorkOrder().workOrderId(),
                ticket.getWorkOrder().sensorId().value(),
                ticket.getWorkOrder().delayed(),
                ticket.getWorkOrder().delayMillis(),
                ticket.getTicketStatus(),
                ticket.getCompletedAt()
        );
    }
}
