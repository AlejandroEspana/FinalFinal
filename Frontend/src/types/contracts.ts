export type AlertSeverity = 'NORMAL' | 'WARNING' | 'CRITICAL';

export interface SensorPlantStatusDto {
  sensorId: string;
  severity: AlertSeverity;
  lastVibration: number;
  operationalStatus: string;
  statusDescription: string;
  lastUpdated: string;
}

export interface HumanActionRequestDto {
  alertId: string;
  sensorId: string;
  severity: AlertSeverity;
  triggerValue: number;
  description: string;
  availableOptions: string[];
  timestamp: string;
}

export interface OperatorDecisionRequestDto {
  alertId: string;
  sensorId: string;
  chosenAction: string;
  operatorId: string;
  notes: string;
}

export interface DecisionDispatchedResponseDto {
  status: string;
  message: string;
  alertId: string;
  sensorId: string;
  actionDispatched: string;
  auditRecordId: number;
  routingTarget: string;
  processedAt: string;
}

export interface AuditRecordDto {
  id: number;
  alertId: string;
  sensorId: string;
  chosenAction: string;
  operatorId: string;
  status: string;
  routingTarget: string;
  delayMillis: number;
  notes: string;
  recordedAt: string;
}

export interface ActuatorStatusResponseDto {
  commandId: string;
  sensorId: string;
  previousState: string;
  currentState: string;
  operatorId: string;
  reason: string;
  executedAt: string;
}

export interface MaintenanceTicketResponseDto {
  workOrderId: string;
  sensorId: string;
  priority: string;
  delayed: boolean;
  delayMillis: number;
  ticketStatus: string;
  executionSummary: string;
  operatorId: string;
  completedAt: string;
}

export interface OperatorCredentials {
  username: string;
  password: string;
}

export interface DelayedCountdownItem {
  alertId: string;
  sensorId: string;
  remainingMs: number;
  totalMs: number;
}
