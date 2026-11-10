package com.empresa.mantenimiento.infrastructure.persistence.repository;

import com.empresa.mantenimiento.domain.model.MaintenanceExecutionTicket;
import com.empresa.mantenimiento.domain.repository.MaintenanceTicketRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryMaintenanceTicketRepository implements MaintenanceTicketRepositoryPort {

    private final List<MaintenanceExecutionTicket> tickets = new CopyOnWriteArrayList<>();

    @Override
    public void saveTicket(MaintenanceExecutionTicket ticket) {
        tickets.add(0, ticket);
    }

    @Override
    public List<MaintenanceExecutionTicket> findAllTickets() {
        return Collections.unmodifiableList(new ArrayList<>(tickets));
    }
}
