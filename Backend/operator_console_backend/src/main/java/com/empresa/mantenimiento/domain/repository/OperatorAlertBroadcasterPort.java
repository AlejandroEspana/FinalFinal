package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;

/**
 * Puerto de Dominio (Inversión de Dependencias - DIP) para difundir alertas interactivas por WebSocket.
 */
public interface OperatorAlertBroadcasterPort {

    void broadcastAlert(HumanActionRequest request);
}
