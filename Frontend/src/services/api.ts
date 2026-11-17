import {
  ActuatorStatusResponseDto,
  AuditRecordDto,
  DecisionDispatchedResponseDto,
  HumanActionRequestDto,
  MaintenanceTicketResponseDto,
  OperatorCredentials,
  OperatorDecisionRequestDto,
  SensorPlantStatusDto
} from '../types/contracts';

const hostname = typeof window !== 'undefined' ? window.location.hostname || 'localhost' : 'localhost';
const wsProtocol = typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'wss:' : 'ws:';

export const WS_ENDPOINTS = {
  plantPrimary: `${wsProtocol}//${hostname}:8081/ws-plant`,
  plantProxy: `${wsProtocol}//${window.location.host}/ws-plant`,
  alertsPrimary: `${wsProtocol}//${hostname}:8082/ws-alerts`,
  alertsProxy: `${wsProtocol}//${window.location.host}/ws-alerts`
};

function buildBasicAuthHeader(creds: OperatorCredentials): string {
  return 'Basic ' + btoa(`${creds.username}:${creds.password}`);
}

async function fetchWithFallback<T>(primaryUrl: string, fallbackUrl: string, init?: RequestInit): Promise<T> {
  try {
    const res = await fetch(primaryUrl, init);
    if (!res.ok) {
      const errBody = await res.text().catch(() => '');
      throw new Error(`HTTP ${res.status}: ${errBody || res.statusText}`);
    }
    if (res.status === 204) {
      return undefined as T;
    }
    return (await res.json()) as T;
  } catch (primaryErr) {
    // Si el error fue un 401/403/400 explícito del servidor, no ocultarlo: propagarlo directamente
    if (primaryErr instanceof Error && /^HTTP (400|401|403|422)/.test(primaryErr.message)) {
      throw primaryErr;
    }
    const resFallback = await fetch(fallbackUrl, init);
    if (!resFallback.ok) {
      const errBody = await resFallback.text().catch(() => '');
      throw new Error(`HTTP ${resFallback.status}: ${errBody || resFallback.statusText}`);
    }
    if (resFallback.status === 204) {
      return undefined as T;
    }
    return (await resFallback.json()) as T;
  }
}

export async function fetchPlantSensorsStatus(): Promise<SensorPlantStatusDto[]> {
  return fetchWithFallback<SensorPlantStatusDto[]>(
    `/api/plant/status`,
    `http://${hostname}:8081/api/v1/sensors/status`
  );
}

export async function injectTelemetryReading(
  sensorId: string,
  vibrationValue: number,
  burstCount: number = 1
): Promise<void> {
  await fetchWithFallback<unknown>(
    `/api/simulation/inject`,
    `http://${hostname}:8089/api/v1/simulation/inject`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sensorId,
        vibrationValue,
        burstCount
      })
    }
  );
}

export async function fetchPendingAlerts(): Promise<HumanActionRequestDto[]> {
  return fetchWithFallback<HumanActionRequestDto[]>(
    `/api/alerts/pending`,
    `http://${hostname}:8082/api/v1/alerts/pending`
  );
}

export async function dismissPendingAlert(alertId: string): Promise<void> {
  await fetchWithFallback<void>(
    `/api/alerts/${encodeURIComponent(alertId)}`,
    `http://${hostname}:8082/api/v1/alerts/${encodeURIComponent(alertId)}`,
    { method: 'DELETE' }
  );
}

export async function submitOperatorDecision(
  request: OperatorDecisionRequestDto,
  creds: OperatorCredentials
): Promise<DecisionDispatchedResponseDto> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: buildBasicAuthHeader(creds)
  };

  const response = await fetchWithFallback<DecisionDispatchedResponseDto>(
    `/api/dispatcher/decide`,
    `http://${hostname}:8080/decide`,
    {
      method: 'POST',
      headers,
      body: JSON.stringify(request)
    }
  );

  // Limpiar la alerta pendiente en operator_console_backend para que no crezca la memoria
  try {
    await dismissPendingAlert(request.alertId);
  } catch (e) {
    console.warn('No se pudo eliminar la alerta de operator_console_backend:', e);
  }

  // Si es parada de emergencia inmediata, reflejar estado detenido en plant_monitor_backend
  if (request.chosenAction === 'APAGADO_INMEDIATO') {
    try {
      await fetchWithFallback(
        `/api/plant/${encodeURIComponent(request.sensorId)}/halt`,
        `http://${hostname}:8081/api/v1/sensors/${encodeURIComponent(request.sensorId)}/halt`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ reason: `Parada de emergencia ejecutada por ${creds.username}: ${request.notes}` })
        }
      );
    } catch (e) {
      console.warn('No se pudo notificar halt a plant_monitor_backend:', e);
    }
  }

  return response;
}

export async function resetSensorAndMachine(sensorId: string, creds: OperatorCredentials): Promise<void> {
  const payload = JSON.stringify({
    sensorId,
    operatorId: creds.username,
    reason: `Reactivación operativa autorizada por ${creds.username}`
  });

  // 1. Reiniciar ventana móvil en alert_detector
  await fetchWithFallback(
    `/api/detector/reset`,
    `http://${hostname}:8088/api/v1/detector/reset`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sensorId })
    }
  ).catch(() => {});

  // 2. Reactivar máquina física en las réplicas de actuator_worker
  await Promise.allSettled([
    fetch(`/api/actuators/${encodeURIComponent(sensorId)}/reset`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: payload
    }),
    fetch(`http://${hostname}:8091/api/v1/actuators/${encodeURIComponent(sensorId)}/reset`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: payload
    }),
    fetch(`http://${hostname}:8092/api/v1/actuators/${encodeURIComponent(sensorId)}/reset`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: payload
    })
  ]);

  // 3. Normalizar estado materializado en plant_monitor_backend
  await fetchWithFallback(
    `/api/plant/${encodeURIComponent(sensorId)}/reset`,
    `http://${hostname}:8081/api/v1/sensors/${encodeURIComponent(sensorId)}/reset`,
    { method: 'POST' }
  );
}

export async function fetchAuditHistory(creds: OperatorCredentials): Promise<AuditRecordDto[]> {
  return fetchWithFallback<AuditRecordDto[]>(
    `/api/dispatcher/api/v1/audit`,
    `http://${hostname}:8080/api/v1/audit`,
    {
      headers: {
        Authorization: buildBasicAuthHeader(creds)
      }
    }
  );
}

export async function fetchActuatorHistoryAllReplicas(): Promise<ActuatorStatusResponseDto[]> {
  const urls = [
    `/api/actuators/history`,
    `http://${hostname}:8091/api/v1/actuators/history`,
    `http://${hostname}:8092/api/v1/actuators/history`
  ];

  const results = await Promise.allSettled(
    urls.map(async (u) => {
      const r = await fetch(u);
      if (!r.ok) return [] as ActuatorStatusResponseDto[];
      return (await r.json()) as ActuatorStatusResponseDto[];
    })
  );

  const map = new Map<string, ActuatorStatusResponseDto>();
  for (const res of results) {
    if (res.status === 'fulfilled' && Array.isArray(res.value)) {
      for (const item of res.value) {
        if (item && item.commandId) {
          map.set(item.commandId, item);
        }
      }
    }
  }

  return Array.from(map.values()).sort(
    (a, b) => new Date(b.executedAt).getTime() - new Date(a.executedAt).getTime()
  );
}

export async function fetchMaintenanceTicketsAllReplicas(): Promise<MaintenanceTicketResponseDto[]> {
  const urls = [
    `/api/maintenance/tickets`,
    `http://${hostname}:8093/api/v1/maintenance/tickets`,
    `http://${hostname}:8094/api/v1/maintenance/tickets`
  ];

  const results = await Promise.allSettled(
    urls.map(async (u) => {
      const r = await fetch(u);
      if (!r.ok) return [] as MaintenanceTicketResponseDto[];
      return (await r.json()) as MaintenanceTicketResponseDto[];
    })
  );

  const map = new Map<string, MaintenanceTicketResponseDto>();
  for (const res of results) {
    if (res.status === 'fulfilled' && Array.isArray(res.value)) {
      for (const item of res.value) {
        if (item && item.workOrderId) {
          map.set(item.workOrderId, item);
        }
      }
    }
  }

  return Array.from(map.values()).sort(
    (a, b) => new Date(b.completedAt).getTime() - new Date(a.completedAt).getTime()
  );
}
