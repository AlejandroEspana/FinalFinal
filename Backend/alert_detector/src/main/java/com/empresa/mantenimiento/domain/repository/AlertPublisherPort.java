package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.IndustrialAlert;

public interface AlertPublisherPort {

    void publishAlert(IndustrialAlert alert);
}
