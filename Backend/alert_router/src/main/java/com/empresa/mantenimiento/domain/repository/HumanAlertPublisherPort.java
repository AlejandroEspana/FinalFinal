package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;

/**
 * Puerto de Dominio (Inversión de Dependencias - DIP) para publicar en el Fanout Exchange human_alerts.
 */
public interface HumanAlertPublisherPort {

    void publish(HumanActionRequest request);
}
