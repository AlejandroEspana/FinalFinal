package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;

public interface OperatorAlertBroadcasterPort {

    void broadcastAlert(HumanActionRequest request);
}
