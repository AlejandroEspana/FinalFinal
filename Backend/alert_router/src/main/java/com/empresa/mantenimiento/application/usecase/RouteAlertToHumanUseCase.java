package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.domain.model.IndustrialAlert;
import com.empresa.mantenimiento.domain.repository.HumanAlertPublisherPort;
import com.empresa.mantenimiento.domain.service.DecisionOptionsPolicyDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class RouteAlertToHumanUseCase {

    private static final Logger log = LoggerFactory.getLogger(RouteAlertToHumanUseCase.class);

    private final DecisionOptionsPolicyDomainService optionsPolicy;
    private final HumanAlertPublisherPort publisherPort;

    public RouteAlertToHumanUseCase(
            DecisionOptionsPolicyDomainService optionsPolicy,
            HumanAlertPublisherPort publisherPort
    ) {
        this.optionsPolicy = Objects.requireNonNull(optionsPolicy, "optionsPolicy no puede ser nulo");
        this.publisherPort = Objects.requireNonNull(publisherPort, "publisherPort no puede ser nulo");
    }

    public HumanActionRequest execute(IndustrialAlert alert) {
        Objects.requireNonNull(alert, "alert no puede ser nula");
        List<String> options = optionsPolicy.resolveOptionsFor(alert.severity());

        HumanActionRequest request = new HumanActionRequest(
                alert.alertId(),
                alert.sensorId(),
                alert.severity(),
                alert.triggerValue(),
                alert.description(),
                options,
                alert.timestamp()
        );

        log.info("Enrutando alerta {} [{}] hacia RabbitMQ Fanout con opciones {}",
                alert.alertId(), alert.severity(), options);

        publisherPort.publish(request);
        return request;
    }
}
