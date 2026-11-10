package com.empresa.mantenimiento.infrastructure.external;

import org.springframework.stereotype.Component;

@Component
public class ErpWorkOrderConnectorClient {

    public boolean notifyErpTicketCreated(String workOrderId) {
        return workOrderId != null && !workOrderId.isBlank();
    }
}
