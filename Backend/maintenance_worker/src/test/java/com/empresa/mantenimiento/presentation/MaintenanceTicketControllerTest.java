package com.empresa.mantenimiento.presentation;

import com.empresa.mantenimiento.application.dto.MaintenanceTicketDto;
import com.empresa.mantenimiento.application.service.MaintenanceOrchestrationService;
import com.empresa.mantenimiento.presentation.controller.MaintenanceTicketController;
import com.empresa.mantenimiento.presentation.mapper.MaintenancePresentationMapper;
import com.empresa.mantenimiento.presentation.response.MaintenanceTicketResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceTicketControllerTest {

    @Mock
    private MaintenanceOrchestrationService orchestrationService;

    private MaintenanceTicketController controller;

    @BeforeEach
    void setUp() {
        controller = new MaintenanceTicketController(orchestrationService, new MaintenancePresentationMapper());
    }

    @Test
    @DisplayName("Debe retornar tickets de mantenimiento en GET /api/v1/maintenance/tickets")
    void shouldReturnAllTickets() {
        when(orchestrationService.getAllTickets()).thenReturn(List.of(
                new MaintenanceTicketDto("WO-1", "sensor_B", "Urgente", true, 10000L, "operator", "COMPLETED", "OK", Instant.now(), Instant.now())
        ));

        ResponseEntity<List<MaintenanceTicketResponse>> response = controller.getAllTickets();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).workOrderId()).isEqualTo("WO-1");
    }
}
