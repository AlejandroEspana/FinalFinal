package com.empresa.mantenimiento.infrastructure;

import com.empresa.mantenimiento.application.dto.AuditRecordDto;
import com.empresa.mantenimiento.domain.model.AlertId;
import com.empresa.mantenimiento.domain.model.MaintenanceDecision;
import com.empresa.mantenimiento.domain.model.OperatorDecisionRecord;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.infrastructure.adapter.JpaDecisionAuditRepositoryAdapter;
import com.empresa.mantenimiento.infrastructure.persistence.entity.OperatorDecisionAuditEntity;
import com.empresa.mantenimiento.infrastructure.persistence.mapper.DecisionAuditPersistenceMapper;
import com.empresa.mantenimiento.infrastructure.persistence.repository.SpringDataDecisionAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaDecisionAuditRepositoryAdapterTest {

    @Mock
    private SpringDataDecisionAuditRepository jpaRepository;

    private JpaDecisionAuditRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaDecisionAuditRepositoryAdapter(jpaRepository, new DecisionAuditPersistenceMapper());
    }

    @Test
    @DisplayName("Debe mapear MaintenanceDecision a OperatorDecisionAuditEntity y persistir en PostgreSQL")
    void shouldPersistDecisionAuditInPostgreSql() {
        OperatorDecisionRecord record = new OperatorDecisionRecord(
                new AlertId("ALT-PG-01"),
                new SensorId("sensor_A"),
                MaintenanceDecision.ACTION_SHUTDOWN_NOW,
                "operator_aws",
                Instant.now(),
                "Auditoría transaccional PostgreSQL"
        );
        MaintenanceDecision decision = new MaintenanceDecision(record);
        decision.aplicarAccion(10000L);

        OperatorDecisionAuditEntity savedEntity = new OperatorDecisionAuditEntity(
                "ALT-PG-01",
                "sensor_A",
                MaintenanceDecision.ACTION_SHUTDOWN_NOW,
                "operator_aws",
                "DISPATCHED",
                "critical_actions_queue",
                0L,
                "Auditoría transaccional PostgreSQL",
                Instant.now()
        );
        when(jpaRepository.save(any(OperatorDecisionAuditEntity.class))).thenReturn(savedEntity);

        Long id = adapter.recordAudit(decision);

        ArgumentCaptor<OperatorDecisionAuditEntity> captor = ArgumentCaptor.forClass(OperatorDecisionAuditEntity.class);
        verify(jpaRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getAlertId()).isEqualTo("ALT-PG-01");
        assertThat(captor.getValue().getSensorId()).isEqualTo("sensor_A");
        assertThat(captor.getValue().getStatus()).isEqualTo("DISPATCHED");
        assertThat(captor.getValue().getRoutingTarget()).isEqualTo("critical_actions_queue");
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("Debe consultar historial de auditoría filtrado por sensorId desde PostgreSQL")
    void shouldFindAuditHistoryBySensorId() {
        OperatorDecisionAuditEntity entity = new OperatorDecisionAuditEntity(
                "ALT-PG-02",
                "sensor_B",
                MaintenanceDecision.ACTION_ACK_WAIT_24H,
                "operator_aws",
                "DISPATCHED",
                "delayed_actions_exchange (x-delay: 10000 ms)",
                10000L,
                "Diferido 24h",
                Instant.now()
        );
        when(jpaRepository.findBySensorIdOrderByRecordedAtDesc("sensor_B")).thenReturn(List.of(entity));

        List<AuditRecordDto> history = adapter.findBySensorId("sensor_B");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).sensorId()).isEqualTo("sensor_B");
        assertThat(history.get(0).delayMillis()).isEqualTo(10000L);
    }
}
