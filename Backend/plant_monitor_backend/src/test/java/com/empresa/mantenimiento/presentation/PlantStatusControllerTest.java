package com.empresa.mantenimiento.presentation;

import com.empresa.mantenimiento.application.service.PlantMonitorApplicationService;
import com.empresa.mantenimiento.application.usecase.UpdatePlantStatusUseCase;
import com.empresa.mantenimiento.domain.repository.PlantStatusBroadcasterPort;
import com.empresa.mantenimiento.domain.service.PlantMaterializedDomainService;
import com.empresa.mantenimiento.presentation.controller.PlantStatusController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: PlantStatusController")
class PlantStatusControllerTest {

    @Mock
    private PlantStatusBroadcasterPort broadcasterPort;

    @Test
    @DisplayName("Retorna la lista de los 10 sensores inicializados")
    void shouldReturnTenSensors() {
        PlantMaterializedDomainService domainService = new PlantMaterializedDomainService();
        UpdatePlantStatusUseCase useCase = new UpdatePlantStatusUseCase(domainService, broadcasterPort);
        PlantMonitorApplicationService appService = new PlantMonitorApplicationService(domainService, useCase);
        PlantStatusController controller = new PlantStatusController(appService);

        var resp = controller.getAllStatuses();
        assertThat(resp.getBody()).hasSize(10);
    }
}
