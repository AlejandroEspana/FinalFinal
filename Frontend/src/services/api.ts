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

const wsProtocol = typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'wss:' : 'ws:';
const hostOrigin = typeof window !== 'undefined' ? window.location.host : '';

// Comunicación exclusiva a través del Reverse Proxy Nginx sin exponer ningún puerto interno
export const WS_ENDPOINTS = {
  plantPrimary: `${wsProtocol}//${hostOrigin}/ws-plant`,
  plantFallback: `${wsProtocol}//${hostOrigin}/ws-plant`,
  alertsPrimary: `${wsProtocol}//${hostOrigin}/ws-alerts`,
  alertsFallback: `${wsProtocol}//${hostOrigin}/ws-alerts`
};

function buildBasicAuthHeader(creds: OperatorCredentials): string {
  return 'Basic ' + btoa(`${creds.username}:${creds.password}`);
}

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init);
  if (!res.ok) {
    const errBody = await res.text().catch(() => '');
    throw new Error(`HTTP ${res.status}: ${errBody || res.statusText}`);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export async function fetchPlantSensorsStatus(): Promise<SensorPlantStatusDto[]> {
  return fetchJson<SensorPlantStatusDto[]>(`/api/plant/status`);
}

export async function fetchSimulationState(): Promise<SimulationStateDto> {
  return fetchJson<SimulationStateDto>(`/api/simulation/state`);
}

export async function toggleSimulationState(enabled?: boolean): Promise<SimulationStateDto> {
  return fetchJson<SimulationStateDto>(`/api/simulation/toggle`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(enabled !== undefined ? { enabled } : {})
  });
}

export async function injectTelemetryReading(
  sensorId: string,
  vibrationValue: number,
  burstCount: number = 1
): Promise<void> {
  await fetchJson<unknown>(`/api/simulation/inject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      sensorId,
      vibrationValue,
      burstCount
    })
  });
}

export async function fetchPendingAlerts(): Promise<HumanActionRequestDto[]> {
  return fetchJson<HumanActionRequestDto[]>(`/api/alerts/pending`);
}

export async function dismissPendingAlert(alertId: string): Promise<void> {
  await fetchJson<void>(`/api/alerts/${encodeURIComponent(alertId)}`, {
    method: 'DELETE'
  });
}

export async function submitOperatorDecision(
  request: OperatorDecisionRequestDto,
  creds: OperatorCredentials
): Promise<DecisionDispatchedResponseDto> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: buildBasicAuthHeader(creds)
  };

  const response = await fetchJson<DecisionDispatchedResponseDto>(`/api/dispatcher/decide`, {
    method: 'POST',
    headers,
    body: JSON.stringify(request)
  });

  try {
    await dismissPendingAlert(request.alertId);
  } catch (e) {
    console.warn('No se pudo eliminar la alerta de operator_console_backend:', e);
  }

  if (request.chosenAction === 'APAGADO_INMEDIATO') {
    try {
      await fetchJson(`/api/plant/${encodeURIComponent(request.sensorId)}/halt`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: `Parada de emergencia ejecutada por ${creds.username}: ${request.notes}` })
      });
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

  await fetchJson(`/api/detector/reset`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sensorId })
  }).catch(() => {});

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

  await fetchJson(`/api/plant/${encodeURIComponent(sensorId)}/reset`, {
    method: 'POST'
  });
}

export async function fetchAuditHistory(creds: OperatorCredentials): Promise<AuditRecordDto[]> {
  return fetchJson<AuditRecordDto[]>(`/api/dispatcher/api/v1/audit`, {
    headers: {
      Authorization: buildBasicAuthHeader(creds)
    }
  });
}

export async function fetchNotificationConfig(creds: OperatorCredentials): Promise<NotificationConfigDto> {
  return fetchJson<NotificationConfigDto>(`/api/dispatcher/api/v1/notifications/config`, {
    headers: {
      Authorization: buildBasicAuthHeader(creds)
    }
  });
}

export async function fetchNotificationHistory(creds: OperatorCredentials): Promise<NotificationRecordDto[]> {
  return fetchJson<NotificationRecordDto[]>(`/api/dispatcher/api/v1/notifications/history`, {
    headers: {
      Authorization: buildBasicAuthHeader(creds)
    }
  });
}

export async function triggerTestNotification(
  creds: OperatorCredentials,
  message?: string
): Promise<NotificationRecordDto> {
  return fetchJson<NotificationRecordDto>(`/api/dispatcher/api/v1/notifications/test`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: buildBasicAuthHeader(creds)
    },
    body: JSON.stringify(message ? { message } : {})
  });
}

export async function fetchActuatorHistoryAllReplicas(): Promise<ActuatorStatusResponseDto[]> {
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
