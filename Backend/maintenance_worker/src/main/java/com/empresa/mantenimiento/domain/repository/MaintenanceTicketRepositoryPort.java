package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.MaintenanceExecutionTicket;

import java.util.List;

public interface MaintenanceTicketRepositoryPort {

    void saveTicket(MaintenanceExecutionTicket ticket);

    List<MaintenanceExecutionTicket> findAllTickets();
}
