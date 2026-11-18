package com.empresa.mantenimiento.presentation;

import com.empresa.mantenimiento.application.dto.ActuatorExecutionDto;
import com.empresa.mantenimiento.application.service.ActuatorOrchestrationService;
import com.empresa.mantenimiento.presentation.controller.ActuatorStatusController;
import com.empresa.mantenimiento.presentation.mapper.ActuatorPresentationMapper;
import com.empresa.mantenimiento.presentation.response.ActuatorStatusResponse;
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
class ActuatorStatusControllerTest {

    @Mock
    private ActuatorOrchestrationService orchestrationService;

    private ActuatorStatusController controller;

    @BeforeEach
    void setUp() {
        controller = new ActuatorStatusController(orchestrationService, new ActuatorPresentationMapper());
    }

    @Test
    @DisplayName("Debe retornar historial de ejecuciones físicas en GET /api/v1/actuators/history")
    void shouldReturnExecutionHistory() {
        when(orchestrationService.getExecutionHistory()).thenReturn(List.of(
                new ActuatorExecutionDto("CMD-1", "sensor_A", "RUNNING", "EMERGENCY_HALTED", "operator", "Crítico", Instant.now())
        ));

        ResponseEntity<List<ActuatorStatusResponse>> response = controller.getExecutionHistory();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).currentState()).isEqualTo("EMERGENCY_HALTED");
    }
}
