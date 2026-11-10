package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.IndustrialAlert;

/**
 * Puerto de Dominio (Inversión de Dependencias - DIP) para publicar alertas analíticas.
 */
public interface AlertPublisherPort {

    void publishAlert(IndustrialAlert alert);
}
