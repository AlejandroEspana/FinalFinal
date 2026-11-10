package com.empresa.mantenimiento.application;

import com.empresa.mantenimiento.application.dto.MaintenanceTicketDto;
import com.empresa.mantenimiento.application.usecase.ProcessMaintenanceWorkOrderUseCase;
import com.empresa.mantenimiento.domain.model.MaintenanceExecutionTicket;
import com.empresa.mantenimiento.domain.model.MaintenanceWorkOrder;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.repository.MaintenanceTicketRepositoryPort;
import com.empresa.mantenimiento.domain.service.MaintenanceSchedulingDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProcessMaintenanceWorkOrderUseCaseTest {

    @Mock
    private MaintenanceTicketRepositoryPort repositoryPort;

    private ProcessMaintenanceWorkOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcessMaintenanceWorkOrderUseCase(repositoryPort, new MaintenanceSchedulingDomainService());
    }

    @Test
    @DisplayName("Debe procesar orden de mantenimiento y persistir ticket en el puerto")
    void shouldProcessWorkOrderAndSaveTicket() {
        // Arrange
        MaintenanceWorkOrder wo = new MaintenanceWorkOrder(
                "WO-IMM-10",
                new SensorId("sensor_C"),
                "Inmediato",
                false,
                0L,
                "operator",
                Instant.now()
        );

        // Act
        MaintenanceTicketDto dto = useCase.execute(wo);

        // Assert
        verify(repositoryPort, times(1)).saveTicket(any(MaintenanceExecutionTicket.class));
        assertThat(dto.ticketStatus()).isEqualTo("COMPLETED");
        assertThat(dto.executionSummary()).contains("INMEDIATO");
    }
}
