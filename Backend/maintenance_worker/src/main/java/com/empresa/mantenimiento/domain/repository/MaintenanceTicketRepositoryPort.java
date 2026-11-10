package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.MaintenanceExecutionTicket;

import java.util.List;

/**
 * Puerto de Dominio (DIP) para almacenar y consultar tickets de mantenimiento ejecutados.
 */
public interface MaintenanceTicketRepositoryPort {

    void saveTicket(MaintenanceExecutionTicket ticket);

    List<MaintenanceExecutionTicket> findAllTickets();
}
