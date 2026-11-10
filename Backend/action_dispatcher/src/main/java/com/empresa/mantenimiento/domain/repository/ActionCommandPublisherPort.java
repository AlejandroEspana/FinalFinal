package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.MaintenanceWorkOrder;
import com.empresa.mantenimiento.domain.model.ShutdownCommand;

/**
 * Puerto de Dominio (Inversión de Dependencias - DIP) para el despacho de órdenes de trabajo hacia RabbitMQ.
 */
public interface ActionCommandPublisherPort {

    void dispatchShutdown(ShutdownCommand command);

    void dispatchImmediateMaintenance(MaintenanceWorkOrder workOrder);

    void dispatchDelayedMaintenance(MaintenanceWorkOrder workOrder, long delayMillis);
}
