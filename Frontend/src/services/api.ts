import {
  ActuatorStatusResponseDto,
  AuditRecordDto,
  DecisionDispatchedResponseDto,
  HumanActionRequestDto,
  MaintenanceTicketResponseDto,
  NotificationConfigDto,
  NotificationRecordDto,
  OperatorCredentials,
  OperatorDecisionRequestDto,
  SensorPlantStatusDto,
  SimulationStateDto
} from '../types/contracts';

const hostname = typeof window !== 'undefined' ? window.location.hostname || 'localhost' : 'localhost';
const wsProtocol = typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'wss:' : 'ws:';

// Prioriza el Reverse Proxy Nginx (/ws-plant y /ws-alerts) para ocultar puertos internos y soportar HTTPS (wss://)
export const WS_ENDPOINTS = {
  plantPrimary: `${wsProtocol}//${window.location.host}/ws-plant`,
  plantFallback: `${wsProtocol}//${hostname}:8081/ws-plant`,
  alertsPrimary: `${wsProtocol}//${window.location.host}/ws-alerts`,
  alertsFallback: `${wsProtocol}//${hostname}:8082/ws-alerts`
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

export async function fetchSimulationState(): Promise<SimulationStateDto> {
  return fetchWithFallback<SimulationStateDto>(
    `/api/simulation/state`,
    `http://${hostname}:8089/api/v1/simulation/state`
  );
}

export async function toggleSimulationState(enabled?: boolean): Promise<SimulationStateDto> {
  return fetchWithFallback<SimulationStateDto>(
    `/api/simulation/toggle`,
    `http://${hostname}:8089/api/v1/simulation/toggle`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(enabled !== undefined ? { enabled } : {})
    }
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

  try {
    await dismissPendingAlert(request.alertId);
  } catch (e) {
    console.warn('No se pudo eliminar la alerta de operator_console_backend:', e);
  }

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

  await fetchWithFallback(
    `/api/detector/reset`,
    `http://${hostname}:8088/api/v1/detector/reset`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sensorId })
    }
  ).catch(() => {});

  // Enviar a ambas réplicas a través del proxy balanceado de Nginx
  await Promise.allSettled([
    fetch(`/api/actuators/${encodeURIComponent(sensorId)}/reset`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: payload
    }),
    fetch(`/api/actuators/${encodeURIComponent(sensorId)}/reset`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: payload
    })
  ]);

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

export async function fetchNotificationConfig(creds: OperatorCredentials): Promise<NotificationConfigDto> {
  return fetchWithFallback<NotificationConfigDto>(
    `/api/dispatcher/api/v1/notifications/config`,
    `http://${hostname}:8080/api/v1/notifications/config`,
    {
      headers: {
        Authorization: buildBasicAuthHeader(creds)
      }
    }
  );
}

export async function fetchNotificationHistory(creds: OperatorCredentials): Promise<NotificationRecordDto[]> {
  return fetchWithFallback<NotificationRecordDto[]>(
    `/api/dispatcher/api/v1/notifications/history`,
    `http://${hostname}:8080/api/v1/notifications/history`,
    {
      headers: {
        Authorization: buildBasicAuthHeader(creds)
      }
    }
  );
}

export async function triggerTestNotification(
  creds: OperatorCredentials,
  message?: string
): Promise<NotificationRecordDto> {
  return fetchWithFallback<NotificationRecordDto>(
    `/api/dispatcher/api/v1/notifications/test`,
    `http://${hostname}:8080/api/v1/notifications/test`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: buildBasicAuthHeader(creds)
      },
      body: JSON.stringify(message ? { message } : {})
    }
  );
}

export async function fetchActuatorHistoryAllReplicas(): Promise<ActuatorStatusResponseDto[]> {
  // Realiza 3 peticiones al upstream de Nginx (round-robin sobre las 2 réplicas en la red interna)
  const urls = [`/api/actuators/history`, `/api/actuators/history`, `/api/actuators/history`];

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
  const urls = [`/api/maintenance/tickets`, `/api/maintenance/tickets`, `/api/maintenance/tickets`];

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
