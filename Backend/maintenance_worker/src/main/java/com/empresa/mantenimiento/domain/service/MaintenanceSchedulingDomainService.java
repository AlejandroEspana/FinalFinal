package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.domain.model.MaintenanceExecutionTicket;

/**
 * Servicio de Dominio Puro (sin dependencias de Spring) que formatea el banner de ejecución técnica.
 */
public class MaintenanceSchedulingDomainService {

    public String formatMaintenanceBanner(MaintenanceExecutionTicket ticket) {
        return String.format(
                "🔧 [MAINTENANCE WORKER] Orden %s (%s) -> Estado: %s | Detalle: %s",
                ticket.getWorkOrder().workOrderId(),
                ticket.getWorkOrder().delayed() ? "DIFERIDA x-delay" : "INMEDIATA",
                ticket.getTicketStatus(),
                ticket.getExecutionSummary()
        );
    }
}
