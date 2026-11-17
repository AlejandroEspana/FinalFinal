package com.empresa.mantenimiento.presentation;

import com.empresa.mantenimiento.application.dto.DecisionResultDto;
import com.empresa.mantenimiento.application.service.DecisionOrchestrationService;
import com.empresa.mantenimiento.domain.model.OperatorDecisionRecord;
import com.empresa.mantenimiento.presentation.controller.ActionDecisionController;
import com.empresa.mantenimiento.presentation.mapper.DecisionPresentationMapper;
import com.empresa.mantenimiento.presentation.request.OperatorDecisionRequest;
import com.empresa.mantenimiento.presentation.response.DecisionDispatchedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionDecisionControllerTest {

    @Mock
    private DecisionOrchestrationService orchestrationService;

    private ActionDecisionController controller;

    @BeforeEach
    void setUp() {
        controller = new ActionDecisionController(orchestrationService, new DecisionPresentationMapper());
    }

    @Test
    @DisplayName("Debe procesar petición válida POST /decide y retornar DecisionDispatchedResponse")
    void shouldProcessDecisionRequestSuccessfully() {
        // Arrange
        OperatorDecisionRequest request = new OperatorDecisionRequest(
                "ALT-900",
                "sensor_A",
                "APAGADO_INMEDIATO",
                "operator",
                "Acción ejecutada desde consola"
        );
        DecisionResultDto mockResult = new DecisionResultDto(
                "SUCCESS",
                "OK",
                "ALT-900",
                "sensor_A",
                "APAGADO_INMEDIATO",
                55L,
                "critical_actions_queue",
                Instant.now()
        );
        when(orchestrationService.processDecision(any(OperatorDecisionRecord.class))).thenReturn(mockResult);

        // Act
        ResponseEntity<DecisionDispatchedResponse> response = controller.submitDecision(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().auditRecordId()).isEqualTo(55L);
        assertThat(response.getBody().routingTarget()).isEqualTo("critical_actions_queue");
    }

    @Test
    @DisplayName("Seguridad de Auditoría: Usa el usuario autenticado (Authentication) e impide suplantación de operatorId")
    void shouldUseAuthenticatedPrincipalOverSpoofedRequestOperatorId() {
        // Arrange
        OperatorDecisionRequest spoofedRequest = new OperatorDecisionRequest(
                "ALT-901",
                "sensor_B",
                "PROGRAMAR_MANTENIMIENTO_AHORA",
                "fake_admin_spoof",
                "Intento de firma en nombre de otro"
        );
        Authentication auth = new UsernamePasswordAuthenticationToken("operator", "N/A");
        DecisionResultDto mockResult = new DecisionResultDto(
                "SUCCESS", "OK", "ALT-901", "sensor_B", "PROGRAMAR_MANTENIMIENTO_AHORA", 56L, "maintenance_queue", Instant.now()
        );
        when(orchestrationService.processDecision(any(OperatorDecisionRecord.class))).thenReturn(mockResult);

        // Act
        controller.submitDecision(spoofedRequest, auth);

        // Assert
        ArgumentCaptor<OperatorDecisionRecord> captor = ArgumentCaptor.forClass(OperatorDecisionRecord.class);
        verify(orchestrationService).processDecision(captor.capture());
        assertThat(captor.getValue().operatorId()).isEqualTo("operator");
    }
}
