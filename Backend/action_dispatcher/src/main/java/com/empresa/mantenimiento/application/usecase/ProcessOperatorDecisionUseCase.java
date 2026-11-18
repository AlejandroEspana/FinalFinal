package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.application.dto.DecisionResultDto;
import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.domain.repository.ActionCommandPublisherPort;
import com.empresa.mantenimiento.domain.repository.DecisionAuditRepositoryPort;
import com.empresa.mantenimiento.shared.util.CommandIdGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Objects;

@Component
public class ProcessOperatorDecisionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessOperatorDecisionUseCase.class);

    private final DecisionAuditRepositoryPort auditRepositoryPort;
    private final ActionCommandPublisherPort commandPublisherPort;
    private final long delayedMillisConfig;

    public ProcessOperatorDecisionUseCase(
            DecisionAuditRepositoryPort auditRepositoryPort,
            ActionCommandPublisherPort commandPublisherPort,
            @Value("${app.maintenance.delayed-millis:10000}") long delayedMillisConfig
    ) {
        this.auditRepositoryPort = Objects.requireNonNull(auditRepositoryPort, "auditRepositoryPort no puede ser nulo");
        this.commandPublisherPort = Objects.requireNonNull(commandPublisherPort, "commandPublisherPort no puede ser nulo");
        this.delayedMillisConfig = delayedMillisConfig;
    }

    @Transactional
    public DecisionResultDto execute(OperatorDecisionRecord record) {
        Objects.requireNonNull(record, "record no puede ser nulo");

        MaintenanceDecision decision = new MaintenanceDecision(record);
        decision.aplicarAccion(delayedMillisConfig);

        Long auditId = auditRepositoryPort.recordAudit(decision);

        Runnable dispatchAction = () -> dispatchToBroker(record, decision);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchAction.run();
                }
            });
        } else {
            dispatchAction.run();
        }

        return new DecisionResultDto(
                "SUCCESS",
                "Decisión humana auditada en PostgreSQL y comando despachado exitosamente.",
                record.alertId().value(),
                record.sensorId().value(),
                record.chosenAction(),
                auditId,
                decision.getRoutingTarget(),
                decision.getProcessedAt()
        );
    }

    private void dispatchToBroker(OperatorDecisionRecord record, MaintenanceDecision decision) {
        switch (record.chosenAction()) {
            case MaintenanceDecision.ACTION_SHUTDOWN_NOW -> {
                ShutdownCommand cmd = new ShutdownCommand(
                        CommandIdGeneratorUtil.nextId("CMD-SHUTDOWN"),
                        record.sensorId(),
                        "Parada de emergencia ordenada por operador: " + record.notes(),
                        record.operatorId(),
                        Instant.now()
                );
                commandPublisherPort.dispatchShutdown(cmd);
            }
            case MaintenanceDecision.ACTION_MAINTENANCE_NOW -> {
                MaintenanceWorkOrder wo = new MaintenanceWorkOrder(
                        CommandIdGeneratorUtil.nextId("WO-IMM"),
                        record.sensorId(),
                        "Mantenimiento Inmediato Solicitado",
                        false,
                        0L,
                        record.operatorId(),
                        Instant.now()
                );
                commandPublisherPort.dispatchImmediateMaintenance(wo);
            }
            case MaintenanceDecision.ACTION_ACK_WAIT_24H -> {
                MaintenanceWorkOrder wo = new MaintenanceWorkOrder(
                        CommandIdGeneratorUtil.nextId("WO-DELAYED"),
                        record.sensorId(),
                        "Mantenimiento Preventivo Programado (Diferido)",
                        true,
                        decision.getDelayMillis(),
                        record.operatorId(),
                        Instant.now()
                );
                commandPublisherPort.dispatchDelayedMaintenance(wo, decision.getDelayMillis());
            }
            default -> log.info("Acción {} registrada sin despacho a workers.", record.chosenAction());
        }
    }
}
