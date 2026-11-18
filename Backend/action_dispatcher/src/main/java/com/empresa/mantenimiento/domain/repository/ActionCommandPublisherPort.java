package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.MaintenanceWorkOrder;
import com.empresa.mantenimiento.domain.model.ShutdownCommand;

public interface ActionCommandPublisherPort {

    void dispatchShutdown(ShutdownCommand command);

    void dispatchImmediateMaintenance(MaintenanceWorkOrder workOrder);

    void dispatchDelayedMaintenance(MaintenanceWorkOrder workOrder, long delayMillis);
}
