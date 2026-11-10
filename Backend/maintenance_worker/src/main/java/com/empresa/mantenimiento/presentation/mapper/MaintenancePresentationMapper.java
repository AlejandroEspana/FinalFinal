package com.empresa.mantenimiento.presentation.mapper;

import com.empresa.mantenimiento.application.dto.MaintenanceTicketDto;
import com.empresa.mantenimiento.presentation.response.MaintenanceTicketResponse;
import org.springframework.stereotype.Component;

@Component
public class MaintenancePresentationMapper {

    public MaintenanceTicketResponse toResponse(MaintenanceTicketDto dto) {
        return new MaintenanceTicketResponse(
                dto.workOrderId(),
                dto.sensorId(),
                dto.priority(),
                dto.delayed(),
                dto.delayMillis(),
                dto.operatorId(),
                dto.ticketStatus(),
                dto.executionSummary(),
                dto.scheduledAt(),
                dto.completedAt()
        );
    }
}
