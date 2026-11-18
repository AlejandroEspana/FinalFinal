package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;

public interface HumanAlertPublisherPort {

    void publish(HumanActionRequest request);
}
