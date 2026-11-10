package com.empresa.mantenimiento.domain.service;

import org.springframework.stereotype.Service;

@Service
public class DecisionRoutingPolicyDomainService {

    public boolean requiresBrokerDispatch(String chosenAction) {
        return !"IGNORAR_10_MINUTOS".equalsIgnoreCase(chosenAction);
    }
}
