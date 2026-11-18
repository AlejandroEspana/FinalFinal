import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertOctagon,
  AlertTriangle,
  Bell,
  CheckCircle2,
  Clock,
  Cpu,
  Database,
  Download,
  ExternalLink,
  Flame,
  Lock,
  Mail,
  MessageSquare,
  Pause,
  Play,
  Power,
  Radio,
  RefreshCw,
  RotateCcw,
  Send,
  Shield,
  ShieldCheck,
  Smartphone,
  Wrench,
  XCircle,
  Zap
} from 'lucide-react';
import {
  ActuatorStatusResponseDto,
  AlertSeverity,
  AuditRecordDto,
  DelayedCountdownItem,
  HumanActionRequestDto,
  MaintenanceTicketResponseDto,
  NotificationConfigDto,
  NotificationRecordDto,
  OperatorCredentials,
  SensorPlantStatusDto,
  SimulationStateDto
} from './types/contracts';
import { useStompWebSocket } from './hooks/useStompWebSocket';
import {
  WS_ENDPOINTS,
  dismissPendingAlert,
  fetchActuatorHistoryAllReplicas,
  fetchAuditHistory,
  fetchMaintenanceTicketsAllReplicas,
  fetchNotificationConfig,
  fetchNotificationHistory,
  fetchPendingAlerts,
  fetchPlantSensorsStatus,
  fetchSimulationState,
  injectTelemetryReading,
  resetSensorAndMachine,
  submitOperatorDecision,
  toggleSimulationState,
  triggerTestNotification
} from './services/api';
import { Building3DViewer } from './components/Building3DViewer';

const DEFAULT_SENSORS: SensorPlantStatusDto[] = [
  'sensor_A',
  'sensor_B',
  'sensor_C',
  'sensor_D',
  'sensor_E',
  'sensor_F',
  'sensor_G',
  'sensor_H',
  'sensor_I',
  'sensor_J'
].map((id) => ({
  sensorId: id,
  severity: 'NORMAL',
  lastVibration: 0.0,
  operationalStatus: 'CONNECTING',
  statusDescription: 'Sincronizando con plant_monitor_backend...',
  lastUpdated: new Date().toISOString()
}));

const ACTION_LABELS: Record<string, { label: string; badgeClass: string; btnClass: string }> = {
  APAGADO_INMEDIATO: {
    label: '🛑 APAGADO INMEDIATO (Corte Energía)',
    badgeClass: 'bg-red-100 text-red-800 border-red-300',
    btnClass: 'bg-red-700 hover:bg-red-600 text-white border-red-800 shadow-sm'
  },
  IGNORAR_10_MINUTOS: {
    label: '⏱️ IGNORAR 10 MINUTOS (Bajo Riesgo)',
    badgeClass: 'bg-[#EDE5D5] text-[#4A3F35] border-[#D5C7B0]',
    btnClass: 'bg-[#E9DFCE] hover:bg-[#DFD2BC] text-[#2C2523] border-[#CBBBA0]'
  },
  PROGRAMAR_MANTENIMIENTO_AHORA: {
    label: '🔧 PROGRAMAR MANTENIMIENTO AHORA',
    badgeClass: 'bg-amber-100 text-amber-900 border-amber-300',
    btnClass: 'bg-amber-600 hover:bg-amber-500 text-white font-bold border-amber-700 shadow-sm'
  },
  RECONOCER_Y_ESPERAR_24H: {
    label: '⏳ RECONOCER Y ESPERAR 24H (x-delay 10s)',
    badgeClass: 'bg-purple-100 text-purple-900 border-purple-300',
    btnClass: 'bg-[#7A542E] hover:bg-[#664524] text-white border-[#5E3F20] shadow-sm'
  }
};

const HARDENING_LAYERS = [
  {
    layer: '1. Reverse Proxy, TLS & DNS Dinámico',
    tech: 'Nginx + TLS 1.2/1.3 + HSTS + DNS Resolver',
    detail: 'Dominio corporativo con cifrado en tránsito, cabeceras de defensa y ocultamiento total de la red interna de microservicios.'
  },
  {
    layer: '2. Blindaje SSH & Control de Acceso',
    tech: '/etc/ssh/sshd_config.d/01-hardening.conf',
    detail: 'PermitRootLogin no, PasswordAuthentication no, autenticación exclusiva por llave pública, MaxAuthTries 3 y Banner legal.'
  },
  {
    layer: '3. Autenticación PAM & Sudo Seguro',
    tech: 'pwquality + faillock + /etc/sudoers.d/99-hardening',
    detail: 'Defaults use_pty, timestamp_timeout=5, passwd_tries=3 y bloqueo automático tras intentos fallidos.'
  },
  {
    layer: '4. Firewall Host & IPS Fail2ban',
    tech: 'UFW / nftables + Fail2ban (sshd, recidive)',
    detail: 'Política deny incoming por defecto. Bloqueo incremental de IPs maliciosas y escáneres de red.'
  },
  {
    layer: '5. Hardening del Kernel & CloudWatch',
    tech: '/etc/sysctl.d/99-hardening.conf + AWS CloudWatch',
    detail: 'ASLR completo (randomize_va_space=2), tcp_syncookies=1, rp_filter=1 y telemetría de RAM/Swap/Disco hacia AWS CloudWatch.'
  },
  {
    layer: '6. Auditoría Forense & Backups S3 + SHA-256',
    tech: 'auditd + backup-postgres-scada.sh + AWS S3',
    detail: 'Reglas inmutables en /etc/audit/rules.d/ y respaldos diarios de PostgreSQL firmados con SHA-256 y replicados en Amazon S3.'
  }
];

export function App() {
  const [sensors, setSensors] = useState<SensorPlantStatusDto[]>(DEFAULT_SENSORS);
  const [alerts, setAlerts] = useState<HumanActionRequestDto[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditRecordDto[]>([]);
  const [actuatorLogs, setActuatorLogs] = useState<ActuatorStatusResponseDto[]>([]);
  const [maintenanceTickets, setMaintenanceTickets] = useState<MaintenanceTicketResponseDto[]>([]);
  const [delayedCountdowns, setDelayedCountdowns] = useState<DelayedCountdownItem[]>([]);
  const [simState, setSimState] = useState<SimulationStateDto>({ enabled: true, rateMs: 12000 });

  const [notifConfig, setNotifConfig] = useState<NotificationConfigDto>({
    email: 'alejandro.espaname@campusucc.edu.co',
    phone: '+573154613867',
    ntfyTopic: 'arn:aws:sns:us-east-2:148395526595:scada-iot-alertas-ucc',
    ntfySubscribeUrl: '',
    enabled: true
  });
  const [notifHistory, setNotifHistory] = useState<NotificationRecordDto[]>([]);
  const [sendingTestNotif, setSendingTestNotif] = useState(false);

  const [credentials, setCredentials] = useState<OperatorCredentials>({
    username: 'operator',
    password: 'operator123'
  });

  const [notesByAlert, setNotesByAlert] = useState<Record<string, string>>({});
  const [submittingAlertId, setSubmittingAlertId] = useState<string | null>(null);
  const [sensorFilter, setSensorFilter] = useState<string>('ALL');

  const [feedbackBanner, setFeedbackBanner] = useState<{
    type: 'success' | 'error' | 'info';
    title: string;
    detail: string;
  } | null>(null);

  const normalizeId = (raw: unknown): string => {
    if (typeof raw === 'string') return raw;
    if (raw && typeof raw === 'object' && 'value' in raw && typeof (raw as { value: unknown }).value === 'string') {
      return (raw as { value: string }).value;
    }
    return String(raw ?? '');
  };

  const handlePlantStatusMessage = useCallback((incoming: SensorPlantStatusDto) => {
    const cleanSensorId = normalizeId(incoming.sensorId);
    setSensors((prev) => {
      const exists = prev.some((s) => s.sensorId === cleanSensorId);
      const updatedItem: SensorPlantStatusDto = {
        ...incoming,
        sensorId: cleanSensorId,
        operationalStatus: incoming.operationalStatus || 'ACTIVE'
      };
      if (!exists) {
        return [...prev, updatedItem].sort((a, b) => a.sensorId.localeCompare(b.sensorId));
      }
      return prev.map((s) => (s.sensorId === cleanSensorId ? updatedItem : s));
    });
  }, []);

  const handleOperatorAlertMessage = useCallback((incoming: HumanActionRequestDto) => {
    const cleanAlertId = normalizeId(incoming.alertId);
    const cleanSensorId = normalizeId(incoming.sensorId);
    const normalizedAlert: HumanActionRequestDto = {
      ...incoming,
      alertId: cleanAlertId,
      sensorId: cleanSensorId,
      availableOptions: Array.isArray(incoming.availableOptions) ? incoming.availableOptions : []
    };

    setAlerts((prev) => {
      const filtered = prev.filter((a) => a.alertId !== cleanAlertId);
      return [normalizedAlert, ...filtered].slice(0, 50);
    });
  }, []);

  const plantWs = useStompWebSocket<SensorPlantStatusDto>({
    url: WS_ENDPOINTS.plantPrimary,
    fallbackUrl: WS_ENDPOINTS.plantFallback,
    topic: '/topic/plant-status',
    onMessage: handlePlantStatusMessage
  });

  const alertsWs = useStompWebSocket<HumanActionRequestDto>({
    url: WS_ENDPOINTS.alertsPrimary,
    fallbackUrl: WS_ENDPOINTS.alertsFallback,
    topic: '/topic/operator-alerts',
    onMessage: handleOperatorAlertMessage
  });

  const refreshAllData = useCallback(async () => {
    const [plantRes, alertsRes, auditRes, actuatorRes, maintRes, simRes, notifCfgRes, notifHistRes] =
      await Promise.allSettled([
        fetchPlantSensorsStatus(),
        fetchPendingAlerts(),
        fetchAuditHistory(credentials),
        fetchActuatorHistoryAllReplicas(),
        fetchMaintenanceTicketsAllReplicas(),
        fetchSimulationState(),
        fetchNotificationConfig(credentials),
        fetchNotificationHistory(credentials)
      ]);

    if (plantRes.status === 'fulfilled' && Array.isArray(plantRes.value) && plantRes.value.length > 0) {
      setSensors(
        plantRes.value
          .map((s) => ({
            ...s,
            sensorId: normalizeId(s.sensorId),
            operationalStatus: s.operationalStatus || 'ACTIVE'
          }))
          .sort((a, b) => a.sensorId.localeCompare(b.sensorId))
      );
    }

    if (alertsRes.status === 'fulfilled' && Array.isArray(alertsRes.value)) {
      setAlerts(
        alertsRes.value.map((a) => ({
          ...a,
          alertId: normalizeId(a.alertId),
          sensorId: normalizeId(a.sensorId),
          availableOptions: Array.isArray(a.availableOptions) ? a.availableOptions : []
        }))
      );
    }

    if (auditRes.status === 'fulfilled' && Array.isArray(auditRes.value)) {
      setAuditLogs(auditRes.value);
    }

    if (actuatorRes.status === 'fulfilled') {
      setActuatorLogs(actuatorRes.value);
    }

    if (maintRes.status === 'fulfilled') {
      setMaintenanceTickets(maintRes.value);
    }

    if (simRes.status === 'fulfilled' && simRes.value) {
      setSimState(simRes.value);
    }

    if (notifCfgRes.status === 'fulfilled' && notifCfgRes.value) {
      setNotifConfig(notifCfgRes.value);
    }

    if (notifHistRes.status === 'fulfilled' && Array.isArray(notifHistRes.value)) {
      setNotifHistory(notifHistRes.value);
    }
  }, [credentials]);

  useEffect(() => {
    refreshAllData();
    const pollInterval = window.setInterval(refreshAllData, 5000);
    return () => window.clearInterval(pollInterval);
  }, [refreshAllData]);

  useEffect(() => {
    if (delayedCountdowns.length === 0) return;
    const timer = window.setInterval(() => {
      setDelayedCountdowns((prev) =>
        prev
          .map((item) => ({ ...item, remainingMs: Math.max(0, item.remainingMs - 250) }))
          .filter((item) => item.remainingMs > 0)
      );
    }, 250);
    return () => window.clearInterval(timer);
  }, [delayedCountdowns.length]);

  const handleToggleSimulation = async () => {
    try {
      const updated = await toggleSimulationState(!simState.enabled);
      setSimState(updated);
      setFeedbackBanner({
        type: 'info',
        title: updated.enabled
          ? `Simulación automática Kafka ACTIVADA (cada ${updated.rateMs / 1000}s)`
          : 'Simulación automática Kafka PAUSADA (Modo Manual para Demostración)',
        detail: updated.enabled
          ? '97% lecturas normales, 2% advertencia, 1% crítico para evitar saturación de alertas.'
          : 'Ahora solo se emitirán alertas cuando presiones los botones manuales en el Edificio 3D o tarjetas.'
      });
    } catch (err) {
      setFeedbackBanner({
        type: 'error',
        title: 'No se pudo cambiar el estado del simulador',
        detail: err instanceof Error ? err.message : String(err)
      });
    }
  };

  const handleInjectSpike = async (sensorId: string, vibrationValue: number, burstCount: number) => {
    try {
      await injectTelemetryReading(sensorId, vibrationValue, burstCount);
      setFeedbackBanner({
        type: 'info',
        title: `Telemetría inyectada en Kafka (${sensorId})`,
        detail:
          burstCount > 1
            ? `Se enviaron ${burstCount} lecturas de ${vibrationValue.toFixed(1)} mm/s al topic sensor_data para activar Regla 2 (Advertencia Stateful).`
            : `Se envió pico crítico de ${vibrationValue.toFixed(1)} mm/s al topic sensor_data para activar Regla 1 (Crítica > 90).`
      });
    } catch (err) {
      setFeedbackBanner({
        type: 'error',
        title: `Error real al inyectar lectura en ${sensorId}`,
        detail: err instanceof Error ? err.message : String(err)
      });
    }
  };

  const handleResetMachine = async (sensorId: string) => {
    try {
      await resetSensorAndMachine(sensorId, credentials);
      setFeedbackBanner({
        type: 'success',
        title: `Máquina ${sensorId} reactivada y ventana reiniciada`,
        detail: `Estado restablecido a RUNNING / NORMAL en plant_monitor_backend, alert_detector y actuator_worker.`
      });
      await refreshAllData();
    } catch (err) {
      setFeedbackBanner({
        type: 'error',
        title: `Error al reactivar ${sensorId}`,
        detail: err instanceof Error ? err.message : String(err)
      });
    }
  };

  const handleDecisionSubmit = async (alert: HumanActionRequestDto, chosenAction: string) => {
    setSubmittingAlertId(alert.alertId);
    const notes =
      notesByAlert[alert.alertId]?.trim() ||
      `Resolución ejecutada desde consola SCADA ante severidad ${alert.severity} (${alert.triggerValue.toFixed(2)} mm/s)`;

    try {
      const result = await submitOperatorDecision(
        {
          alertId: alert.alertId,
          sensorId: alert.sensorId,
          chosenAction,
          operatorId: credentials.username,
          notes
        },
        credentials
      );

      setAlerts((prev) => prev.filter((a) => a.alertId !== alert.alertId));

      if (chosenAction === 'RECONOCER_Y_ESPERAR_24H') {
        setDelayedCountdowns((prev) => [
          ...prev,
          {
            alertId: alert.alertId,
            sensorId: alert.sensorId,
            remainingMs: 10000,
            totalMs: 10000
          }
        ]);
        window.setTimeout(() => {
          refreshAllData();
        }, 10500);
      }

      setFeedbackBanner({
        type: 'success',
        title: `Decisión Auditada en PostgreSQL (#${result.auditRecordId}), Despachada a RabbitMQ y Notificada vía Amazon SNS`,
        detail: `Sensor: ${result.sensorId} | Acción: ${result.actionDispatched} | Boletín ISO-10816 enviado a ${notifConfig.email}`
      });

      await refreshAllData();
    } catch (err) {
      setFeedbackBanner({
        type: 'error',
        title: `Fallo real al procesar decisión en action_dispatcher`,
        detail: err instanceof Error ? err.message : String(err)
      });
    } finally {
      setSubmittingAlertId(null);
    }
  };

  const handleSendTestNotification = async () => {
    setSendingTestNotif(true);
    try {
      const rec = await triggerTestNotification(credentials);
      setFeedbackBanner({
        type: 'success',
        title: `Boletín Técnico Corporativo Despachado (${rec.notificationId})`,
        detail: `Enviado vía Amazon SNS a ${rec.recipient} — Estado: ${rec.deliveryStatus}`
      });
      await refreshAllData();
    } catch (err) {
      setFeedbackBanner({
        type: 'error',
        title: 'Error enviando notificación de prueba',
        detail: err instanceof Error ? err.message : String(err)
      });
    } finally {
      setSendingTestNotif(false);
    }
  };

  const handleExportAuditCsv = () => {
    if (auditLogs.length === 0) return;
    const headers = ['ID', 'Alerta', 'Sensor', 'Accion', 'Operador', 'Estado', 'Cola_RabbitMQ', 'Delay_Ms', 'Notas', 'Fecha_UTC'];
    const rows = auditLogs.map((r) => [
      r.id,
      r.alertId,
      r.sensorId,
      r.chosenAction,
      r.operatorId,
      r.status,
      r.routingTarget,
      r.delayMillis,
      `"${(r.notes || '').replace(/"/g, '""')}"`,
      r.recordedAt
    ]);
    const csvContent = [headers.join(','), ...rows.map((row) => row.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `auditoria_mantenimiento_ucc_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleDismissOnly = async (alertId: string) => {
    try {
      await dismissPendingAlert(alertId);
      setAlerts((prev) => prev.filter((a) => a.alertId !== alertId));
    } catch (err) {
      setFeedbackBanner({
        type: 'error',
        title: 'No se pudo descartar la alerta',
        detail: err instanceof Error ? err.message : String(err)
      });
    }
  };

  const buildAlertWhatsAppUrl = (alert: HumanActionRequestDto) => {
    const cleanPhone = notifConfig.phone.replace(/[^0-9]/g, '') || '573154613867';
    const text =
      `🚨 *BOLETÍN DE ALERTA SCADA IoT (ISO 10816-3)*\n` +
      `• *Folio Alerta:* ${alert.alertId}\n` +
      `• *Activo / Sensor:* ${alert.sensorId}\n` +
      `• *Severidad:* ${alert.severity} (${Number(alert.triggerValue).toFixed(2)} mm/s RMS)\n` +
      `• *Diagnóstico:* ${alert.description}\n` +
      `• *Maniobras:* ${alert.availableOptions.join(' | ')}`;
    return `https://wa.me/${cleanPhone}?text=${encodeURIComponent(text)}`;
  };

  const filteredAudits = useMemo(() => {
    if (sensorFilter === 'ALL') return auditLogs;
    return auditLogs.filter((a) => a.sensorId === sensorFilter);
  }, [auditLogs, sensorFilter]);

  const counts = useMemo(() => {
    const critical = sensors.filter((s) => s.severity === 'CRITICAL' || s.operationalStatus === 'EMERGENCY_HALTED').length;
    const warning = sensors.filter((s) => s.severity === 'WARNING' && s.operationalStatus !== 'EMERGENCY_HALTED').length;
    const normal = sensors.length - critical - warning;
    return { critical, warning, normal };
  }, [sensors]);

  const getSeverityStyles = (severity: AlertSeverity, operationalStatus: string) => {
    if (operationalStatus === 'EMERGENCY_HALTED') {
      return {
        card: 'border-rose-400 bg-rose-50/90 shadow-sm',
        badge: 'bg-rose-700 text-white',
        bar: 'bg-rose-600',
        label: 'DETENIDA (EMERGENCIA)'
      };
    }
    switch (severity) {
      case 'CRITICAL':
        return {
          card: 'border-red-400 bg-red-50/80 shadow-sm',
          badge: 'bg-red-100 text-red-800 border border-red-300',
          bar: 'bg-red-600',
          label: 'CRÍTICO (>90 mm/s)'
        };
      case 'WARNING':
        return {
          card: 'border-amber-400 bg-amber-50/80 shadow-sm',
          badge: 'bg-amber-100 text-amber-800 border border-amber-300',
          bar: 'bg-amber-500',
          label: 'ADVERTENCIA (>75 x3)'
        };
      default:
        return {
          card: 'border-[#E5DCCB] bg-[#FFFDF9] hover:border-[#CBBBA0]',
          badge: 'bg-emerald-100 text-emerald-800 border border-emerald-300',
          bar: 'bg-emerald-600',
          label: 'OPERACIÓN NORMAL'
        };
    }
  };

  return (
    <div className="min-h-screen bg-[#F7F2E9] text-[#2C2523] flex flex-col">
      <header className="sticky top-0 z-30 border-b border-[#E2D6C1] bg-[#FFFDF9]/95 backdrop-blur-md px-6 py-3.5 shadow-sm">
        <div className="max-w-[1600px] mx-auto flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-[#F4ECE0] border border-[#DFD2BC] flex items-center justify-center text-[#8C6239]">
              <Cpu className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-bold tracking-tight text-[#2C2523]">
                  SCADA IoT Industrial & Gemelo Digital 3D — Mantenimiento Predictivo
                </h1>
                <span className="text-[11px] font-mono uppercase px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 border border-emerald-300 font-semibold">
                  AWS EC2 + Amazon SNS + TLS
                </span>
              </div>
              <p className="text-xs text-[#6E6256]">
                Apache Kafka (Stateful) • RabbitMQ (Fanout + Delayed x-delay) • PostgreSQL (ACID) • Boletín ISO-10816 a {notifConfig.email}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <button
              onClick={handleToggleSimulation}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-semibold transition ${
                simState.enabled
                  ? 'bg-[#F4ECE0] border-[#D8C7AE] text-[#6B4A28] hover:bg-[#EAE0D0]'
                  : 'bg-amber-100 border-amber-300 text-amber-900 hover:bg-amber-200'
              }`}
              title="Pausa o reanuda la emisión aleatoria cada 12s en sensor_producer"
            >
              {simState.enabled ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
              <span>{simState.enabled ? 'Simulación Auto (12s): ON' : 'Simulación Auto: PAUSADA'}</span>
            </button>

            <div
              className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-mono font-semibold ${
                plantWs.connected
                  ? 'bg-emerald-50 border-emerald-300 text-emerald-800'
                  : 'bg-amber-50 border-amber-300 text-amber-800'
              }`}
            >
              <Radio className="w-3.5 h-3.5 animate-pulse" />
              <span>WS Planta: {plantWs.connected ? 'ONLINE' : 'SYNC'}</span>
            </div>

            <div
              className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-mono font-semibold ${
                alertsWs.connected
                  ? 'bg-emerald-50 border-emerald-300 text-emerald-800'
                  : 'bg-amber-50 border-amber-300 text-amber-800'
              }`}
            >
              <Radio className="w-3.5 h-3.5 animate-pulse" />
              <span>WS Alertas: {alertsWs.connected ? 'ONLINE' : 'SYNC'}</span>
            </div>

            <div className="flex items-center gap-2 bg-[#F6EFE2] border border-[#DFD2BC] rounded-lg px-3 py-1.5 text-xs">
              <ShieldCheck className="w-4 h-4 text-[#8C6239]" />
              <select
                value={credentials.username}
                onChange={(e) => {
                  const user = e.target.value;
                  setCredentials({
                    username: user,
                    password: user === 'admin' ? 'admin123' : 'operator123'
                  });
                }}
                className="bg-[#FFFDF9] text-[#2C2523] font-mono font-semibold rounded px-2 py-0.5 border border-[#D8C7AE] focus:outline-none"
              >
                <option value="operator">operator (ROLE_OPERATOR)</option>
                <option value="admin">admin (ROLE_ADMIN)</option>
              </select>
            </div>

            <button
              onClick={refreshAllData}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#FAF5EC] hover:bg-[#EFE6D5] text-[#4A3F35] text-xs font-medium border border-[#DFD2BC] transition"
            >
              <RefreshCw className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </header>

      {feedbackBanner && (
        <div className="max-w-[1600px] w-full mx-auto px-6 pt-4">
          <div
            className={`flex items-center justify-between gap-4 px-4 py-3 rounded-xl border text-sm shadow-sm ${
              feedbackBanner.type === 'success'
                ? 'bg-emerald-50 border-emerald-300 text-emerald-950'
                : feedbackBanner.type === 'error'
                ? 'bg-red-50 border-red-300 text-red-950'
                : 'bg-[#FFF9ED] border-[#E0CBA8] text-[#3D2E1E]'
            }`}
          >
            <div className="flex items-center gap-3">
              {feedbackBanner.type === 'success' && <CheckCircle2 className="w-5 h-5 text-emerald-700 shrink-0" />}
              {feedbackBanner.type === 'error' && <XCircle className="w-5 h-5 text-red-700 shrink-0" />}
              {feedbackBanner.type === 'info' && <Zap className="w-5 h-5 text-[#8C6239] shrink-0" />}
              <div>
                <p className="font-bold">{feedbackBanner.title}</p>
                <p className="text-xs opacity-90 font-mono">{feedbackBanner.detail}</p>
              </div>
            </div>
            <button
              onClick={() => setFeedbackBanner(null)}
              className="text-xs px-2.5 py-1 rounded bg-white/80 hover:bg-white text-[#4A3F35] border border-[#DFD2BC] font-semibold"
            >
              Cerrar
            </button>
          </div>
        </div>
      )}

      <main className="max-w-[1600px] w-full mx-auto px-6 py-6 flex-1 space-y-6">
        <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
          <div className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-xl p-4 flex items-center justify-between shadow-sm">
            <div>
              <p className="text-xs text-[#6E6256] uppercase font-semibold">Sensores Normales</p>
              <p className="text-2xl font-bold text-emerald-700 mt-1">{counts.normal} / 10</p>
            </div>
            <Activity className="w-8 h-8 text-emerald-600/30" />
          </div>

          <div className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-xl p-4 flex items-center justify-between shadow-sm">
            <div>
              <p className="text-xs text-[#6E6256] uppercase font-semibold">En Advertencia</p>
              <p className="text-2xl font-bold text-amber-600 mt-1">{counts.warning}</p>
            </div>
            <AlertTriangle className="w-8 h-8 text-amber-500/30" />
          </div>

          <div className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-xl p-4 flex items-center justify-between shadow-sm">
            <div>
              <p className="text-xs text-[#6E6256] uppercase font-semibold">Críticos / Detenidos</p>
              <p className="text-2xl font-bold text-red-700 mt-1">{counts.critical}</p>
            </div>
            <AlertOctagon className="w-8 h-8 text-red-600/30" />
          </div>

          <div className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-xl p-4 flex items-center justify-between shadow-sm">
            <div>
              <p className="text-xs text-[#6E6256] uppercase font-semibold">Alertas Activas</p>
              <p className="text-2xl font-bold text-[#8C6239] mt-1">{alerts.length}</p>
            </div>
            <Flame className="w-8 h-8 text-[#8C6239]/30" />
          </div>

          <div className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-xl p-4 flex items-center justify-between shadow-sm">
            <div>
              <p className="text-xs text-[#6E6256] uppercase font-semibold">Auditorías PostgreSQL</p>
              <p className="text-2xl font-bold text-[#5C4033] mt-1">{auditLogs.length}</p>
            </div>
            <Database className="w-8 h-8 text-[#5C4033]/30" />
          </div>
        </div>

        <Building3DViewer
          sensors={sensors}
          onInjectSpike={handleInjectSpike}
          onResetMachine={handleResetMachine}
        />

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <section className="lg:col-span-7 bg-[#FFFDF9] border border-[#E5DCCB] rounded-2xl p-5 flex flex-col shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-2 pb-4 border-b border-[#EDE4D3]">
              <div>
                <h2 className="text-base font-bold text-[#2C2523] flex items-center gap-2">
                  <Activity className="w-5 h-5 text-[#8C6239]" />
                  Detalle de Sensores por Sector (10 Activos — Kafka Stateful)
                </h2>
                <p className="text-xs text-[#6E6256] mt-0.5">
                  Sincronizado en tiempo real con el modelo 3D superior. Ciclo realista de 12s (97% normal).
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5 mt-4">
              {sensors.map((sensor) => {
                const style = getSeverityStyles(sensor.severity, sensor.operationalStatus);
                const pct = Math.min(100, Math.max(0, (sensor.lastVibration / 110) * 100));
                const isHalted = sensor.operationalStatus === 'EMERGENCY_HALTED';

                return (
                  <div
                    key={sensor.sensorId}
                    className={`rounded-xl border p-4 transition-all flex flex-col justify-between ${style.card}`}
                  >
                    <div>
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span className="font-mono font-bold text-sm text-[#2C2523]">{sensor.sensorId}</span>
                          <span className="text-[11px] font-mono text-[#6E6256]">[{sensor.operationalStatus}]</span>
                        </div>
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${style.badge}`}>
                          {style.label}
                        </span>
                      </div>

                      <div className="mt-3 flex items-baseline justify-between">
                        <div>
                          <span className="text-2xl font-mono font-extrabold text-[#2C2523]">
                            {sensor.lastVibration.toFixed(2)}
                          </span>
                          <span className="text-xs text-[#6E6256] ml-1">mm/s RMS</span>
                        </div>
                        <span className="text-[11px] text-[#6E6256] font-mono">
                          Umbral: 75 / 90 mm/s
                        </span>
                      </div>

                      <div className="w-full h-2 bg-[#EAE0D0] rounded-full overflow-hidden mt-2">
                        <div
                          className={`h-full transition-all duration-300 ${style.bar}`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>

                      <p className="text-xs text-[#4A3F35] mt-2.5 line-clamp-2 min-h-[2rem]">
                        {sensor.statusDescription}
                      </p>
                    </div>

                    <div className="mt-3 pt-2.5 border-t border-[#EDE4D3] flex flex-wrap items-center gap-1.5">
                      <button
                        onClick={() => handleInjectSpike(sensor.sensorId, 96.4, 1)}
                        disabled={isHalted}
                        className="flex-1 text-[11px] font-semibold px-2 py-1.5 rounded bg-red-100 hover:bg-red-200 text-red-900 border border-red-300 disabled:opacity-40 transition"
                      >
                        ⚡ Pico &gt;90
                      </button>

                      <button
                        onClick={() => handleInjectSpike(sensor.sensorId, 82.0, 3)}
                        disabled={isHalted}
                        className="flex-1 text-[11px] font-semibold px-2 py-1.5 rounded bg-amber-100 hover:bg-amber-200 text-amber-900 border border-amber-300 disabled:opacity-40 transition"
                      >
                        ⚠️ Ráfaga 3x &gt;75
                      </button>

                      {(isHalted || sensor.severity !== 'NORMAL') && (
                        <button
                          onClick={() => handleResetMachine(sensor.sensorId)}
                          className="text-[11px] font-semibold px-2.5 py-1.5 rounded bg-emerald-700 hover:bg-emerald-600 text-white flex items-center gap-1 transition"
                        >
                          <RotateCcw className="w-3 h-3" />
                          Reactivar
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          <section className="lg:col-span-5 bg-[#FFFDF9] border border-[#E5DCCB] rounded-2xl p-5 flex flex-col shadow-sm">
            <div className="pb-4 border-b border-[#EDE4D3] flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold text-[#2C2523] flex items-center gap-2">
                  <AlertOctagon className="w-5 h-5 text-amber-600" />
                  Consola Interactiva del Operador (RabbitMQ Fanout)
                </h2>
                <p className="text-xs text-[#6E6256] mt-0.5">
                  Opciones dinámicas desde <code className="text-[#8C6239] font-semibold">alert_router</code> con despacho de boletín ISO-10816 vía Amazon SNS.
                </p>
              </div>
              <span className="px-2.5 py-1 rounded-full text-xs font-mono font-bold bg-[#F4ECE0] text-[#7A542E] border border-[#DFD2BC]">
                {alerts.length} activas
              </span>
            </div>

            {delayedCountdowns.length > 0 && (
              <div className="mt-4 space-y-2">
                {delayedCountdowns.map((item) => {
                  const pct = ((item.totalMs - item.remainingMs) / item.totalMs) * 100;
                  return (
                    <div
                      key={item.alertId}
                      className="rounded-xl bg-[#F8F1E5] border border-[#D8C3A5] p-3 text-xs"
                    >
                      <div className="flex items-center justify-between text-[#5C4033] font-mono">
                        <span className="flex items-center gap-1.5 font-semibold">
                          <Clock className="w-4 h-4 text-[#8C6239] animate-spin" />
                          RabbitMQ Delayed Exchange (x-delay) — {item.sensorId}
                        </span>
                        <span>{(item.remainingMs / 1000).toFixed(1)}s restantes</span>
                      </div>
                      <div className="w-full h-1.5 bg-[#E5DCCB] rounded-full overflow-hidden mt-2">
                        <div
                          className="h-full bg-[#8C6239] transition-all duration-200"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            <div className="mt-4 space-y-3.5 max-h-[620px] overflow-y-auto pr-1 flex-1">
              {alerts.length === 0 ? (
                <div className="h-64 flex flex-col items-center justify-center text-center border border-dashed border-[#DFD2BC] bg-[#FAF6EE] rounded-xl p-6 text-[#6E6256]">
                  <CheckCircle2 className="w-10 h-10 text-emerald-600/70 mb-2" />
                  <p className="text-sm font-bold text-[#2C2523]">Sin alertas pendientes de decisión humana</p>
                  <p className="text-xs text-[#6E6256] mt-1">
                    Selecciona un sensor en el Edificio 3D o en las tarjetas y haz clic en &ldquo;⚡ Pico &gt;90&rdquo; para disparar una alerta real.
                  </p>
                </div>
              ) : (
                alerts.map((alert) => {
                  const isCritical = alert.severity === 'CRITICAL';
                  const isSubmitting = submittingAlertId === alert.alertId;

                  return (
                    <div
                      key={alert.alertId}
                      className={`rounded-xl border p-4 transition ${
                        isCritical
                          ? 'bg-red-50/90 border-red-300'
                          : 'bg-amber-50/90 border-amber-300'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <div className="flex items-center gap-2">
                            <span
                              className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded ${
                                isCritical ? 'bg-red-700 text-white' : 'bg-amber-600 text-white'
                              }`}
                            >
                              {alert.severity}
                            </span>
                            <span className="font-mono font-bold text-sm text-[#2C2523]">
                              {alert.sensorId}
                            </span>
                            <span className="font-mono text-xs text-[#6E6256]">
                              ({alert.alertId})
                            </span>
                          </div>
                          <p className="text-xs text-[#3D342E] mt-1.5 font-medium">{alert.description}</p>
                        </div>

                        <div className="text-right shrink-0">
                          <span className="text-sm font-mono font-bold text-[#2C2523]">
                            {Number(alert.triggerValue).toFixed(2)} mm/s
                          </span>
                          <p className="text-[10px] text-[#6E6256] font-mono">
                            {new Date(alert.timestamp).toLocaleTimeString()}
                          </p>
                        </div>
                      </div>

                      <div className="mt-3">
                        <input
                          type="text"
                          value={notesByAlert[alert.alertId] ?? ''}
                          onChange={(e) =>
                            setNotesByAlert((prev) => ({
                              ...prev,
                              [alert.alertId]: e.target.value
                            }))
                          }
                          placeholder="Observación técnica para el acta de auditoría PostgreSQL y correo SNS..."
                          className="w-full text-xs bg-[#FFFDF9] border border-[#D8C7AE] rounded-lg px-3 py-1.5 text-[#2C2523] placeholder:text-[#8C7E6D] focus:outline-none focus:border-[#8C6239]"
                        />
                      </div>

                      <div className="mt-3 flex flex-col gap-2">
                        {alert.availableOptions.map((option) => {
                          const meta = ACTION_LABELS[option] ?? {
                            label: option,
                            badgeClass: 'bg-[#EDE5D5] text-[#2C2523]',
                            btnClass: 'bg-[#8C6239] hover:bg-[#7A542E] text-white'
                          };
                          return (
                            <button
                              key={option}
                              disabled={isSubmitting}
                              onClick={() => handleDecisionSubmit(alert, option)}
                              className={`w-full py-2 px-3 rounded-lg text-xs font-semibold border transition flex items-center justify-between ${meta.btnClass} disabled:opacity-50`}
                            >
                              <span>{meta.label}</span>
                              <span className="font-mono text-[10px] opacity-85">{option}</span>
                            </button>
                          );
                        })}

                        <div className="flex items-center justify-between pt-1">
                          <a
                            href={buildAlertWhatsAppUrl(alert)}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-emerald-800 hover:text-emerald-700"
                          >
                            <MessageSquare className="w-3.5 h-3.5" />
                            Enviar a WhatsApp ({notifConfig.phone})
                          </a>

                          <button
                            onClick={() => handleDismissOnly(alert.alertId)}
                            className="text-[11px] text-[#6E6256] hover:text-[#2C2523] font-medium"
                          >
                            Descartar de la vista
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </section>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <section className="lg:col-span-6 bg-[#FFFDF9] border border-[#E5DCCB] rounded-2xl p-5 flex flex-col justify-between shadow-sm">
            <div>
              <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-[#EDE4D3]">
                <div className="flex items-center gap-2">
                  <Bell className="w-5 h-5 text-[#8C6239]" />
                  <div>
                    <h3 className="text-sm font-bold text-[#2C2523]">
                      Centro de Boletines Corporativos vía Amazon SNS (ISO 10816-3)
                    </h3>
                    <p className="text-xs text-[#6E6256]">
                      Despacho de actas operativas y alertas críticas mediante infraestructura oficial de AWS SNS.
                    </p>
                  </div>
                </div>

                <button
                  onClick={handleSendTestNotification}
                  disabled={sendingTestNotif}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#8C6239] hover:bg-[#734F2C] text-white text-xs font-semibold transition disabled:opacity-50 shadow-sm"
                >
                  <Send className="w-3.5 h-3.5" />
                  {sendingTestNotif ? 'Enviando...' : 'Emitir Boletín SNS'}
                </button>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-4">
                <div className="bg-[#FAF6EE] border border-[#E5DCCB] rounded-xl p-3 text-xs">
                  <div className="flex items-center gap-1.5 text-[#8C6239] font-bold">
                    <Mail className="w-4 h-4" />
                    <span>Suscripción Correo Amazon SNS</span>
                  </div>
                  <p className="font-mono text-[#2C2523] mt-1 break-all">{notifConfig.email}</p>
                </div>

                <div className="bg-[#FAF6EE] border border-[#E5DCCB] rounded-xl p-3 text-xs">
                  <div className="flex items-center gap-1.5 text-emerald-800 font-bold">
                    <Smartphone className="w-4 h-4" />
                    <span>Línea Móvil de Guardia (SNS / WhatsApp)</span>
                  </div>
                  <p className="font-mono text-[#2C2523] mt-1">{notifConfig.phone}</p>
                </div>
              </div>

              <div className="mt-4 max-h-48 overflow-y-auto space-y-2">
                {notifHistory.length === 0 ? (
                  <p className="text-xs text-[#6E6256] py-6 text-center">
                    Haz clic en &ldquo;Emitir Boletín SNS&rdquo; o resuelve una alerta para ver el historial de envíos en Amazon SNS.
                  </p>
                ) : (
                  notifHistory.map((n) => (
                    <div
                      key={n.notificationId}
                      className="rounded-lg bg-[#FAF6EE] border border-[#E5DCCB] p-2.5 text-xs flex items-center justify-between gap-3"
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-mono font-bold text-[#8C6239]">{n.notificationId}</span>
                          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-800 border border-emerald-300 font-semibold">
                            {n.deliveryStatus}
                          </span>
                        </div>
                        <p className="text-[#2C2523] font-medium truncate mt-0.5">{n.subject}</p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <a
                          href={n.whatsappDirectUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="px-2 py-1 rounded bg-emerald-700 hover:bg-emerald-600 text-white font-semibold text-[11px]"
                        >
                          WhatsApp
                        </a>
                        <span className="font-mono text-[10px] text-[#6E6256]">
                          {new Date(n.sentAt).toLocaleTimeString()}
                        </span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </section>

          <section className="lg:col-span-6 bg-[#FFFDF9] border border-[#E5DCCB] rounded-2xl p-5 shadow-sm">
            <div className="flex items-center justify-between pb-3 border-b border-[#EDE4D3]">
              <div className="flex items-center gap-2">
                <Shield className="w-5 h-5 text-[#8C6239]" />
                <div>
                  <h3 className="text-sm font-bold text-[#2C2523]">
                    Hardening de Ubuntu Server 24.04 LTS en AWS EC2 (Defensa en Profundidad)
                  </h3>
                  <p className="text-xs text-[#6E6256]">
                    Capas de seguridad de Sistemas Operativos activas en el servidor de producción
                  </p>
                </div>
              </div>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-mono font-bold bg-emerald-100 text-emerald-800 border border-emerald-300">
                <Lock className="w-3 h-3" /> 6 Capas Activas
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 mt-3.5">
              {HARDENING_LAYERS.map((item) => (
                <div
                  key={item.layer}
                  className="rounded-xl bg-[#FAF6EE] border border-[#E5DCCB] p-3 text-xs"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-[#2C2523]">{item.layer}</span>
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-700 shrink-0" />
                  </div>
                  <p className="font-mono text-[10px] text-[#8C6239] font-semibold mt-0.5">{item.tech}</p>
                  <p className="text-[#5C5146] text-[11px] mt-1 leading-relaxed">{item.detail}</p>
                </div>
              ))}
            </div>
          </section>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <section className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-2xl p-5 shadow-sm">
            <div className="flex items-center justify-between pb-3 border-b border-[#EDE4D3]">
              <div className="flex items-center gap-2">
                <Power className="w-5 h-5 text-red-700" />
                <div>
                  <h3 className="text-sm font-bold text-[#2C2523]">
                    Actuator Workers — Paradas de Emergencia y Reactivaciones (2 Réplicas)
                  </h3>
                  <p className="text-xs text-[#6E6256]">
                    Cola RabbitMQ: <code className="text-red-800 font-semibold">critical_actions_queue</code> (Balanceo Round-Robin en Red Privada)
                  </p>
                </div>
              </div>
              <span className="text-xs font-mono text-[#6E6256]">{actuatorLogs.length} eventos</span>
            </div>

            <div className="mt-3 max-h-60 overflow-y-auto space-y-2">
              {actuatorLogs.length === 0 ? (
                <p className="text-xs text-[#6E6256] py-8 text-center">
                  Aún no se han ejecutado comandos de corte de energía en actuator_worker.
                </p>
              ) : (
                actuatorLogs.map((log) => (
                  <div
                    key={log.commandId}
                    className="rounded-lg bg-[#FAF6EE] border border-[#E5DCCB] p-3 text-xs flex items-center justify-between gap-3"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono font-bold text-[#2C2523]">{log.sensorId}</span>
                        <span className="font-mono text-[11px] text-[#6E6256]">{log.commandId}</span>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-red-100 text-red-800 border border-red-300 font-semibold">
                          {log.previousState} → {log.currentState}
                        </span>
                      </div>
                      <p className="text-[#4A3F35] mt-1">{log.reason}</p>
                    </div>
                    <div className="text-right font-mono text-[11px] text-[#6E6256] shrink-0">
                      <div>Op: {log.operatorId}</div>
                      <div>{new Date(log.executedAt).toLocaleTimeString()}</div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>

          <section className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-2xl p-5 shadow-sm">
            <div className="flex items-center justify-between pb-3 border-b border-[#EDE4D3]">
              <div className="flex items-center gap-2">
                <Wrench className="w-5 h-5 text-amber-700" />
                <div>
                  <h3 className="text-sm font-bold text-[#2C2523]">
                    Maintenance Workers — Órdenes Inmediatas y Diferidas x-delay (2 Réplicas)
                  </h3>
                  <p className="text-xs text-[#6E6256]">
                    Cola RabbitMQ: <code className="text-amber-800 font-semibold">maintenance_queue</code> + <code className="text-[#8C6239] font-semibold">delayed_actions_exchange</code> (Cluster Interno Aislado)
                  </p>
                </div>
              </div>
              <span className="text-xs font-mono text-[#6E6256]">{maintenanceTickets.length} órdenes</span>
            </div>

            <div className="mt-3 max-h-60 overflow-y-auto space-y-2">
              {maintenanceTickets.length === 0 ? (
                <p className="text-xs text-[#6E6256] py-8 text-center">
                  Aún no se han recibido órdenes de trabajo en maintenance_worker.
                </p>
              ) : (
                maintenanceTickets.map((ticket) => (
                  <div
                    key={ticket.workOrderId}
                    className="rounded-lg bg-[#FAF6EE] border border-[#E5DCCB] p-3 text-xs flex items-center justify-between gap-3"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono font-bold text-[#2C2523]">{ticket.sensorId}</span>
                        <span className="font-mono text-[11px] text-[#6E6256]">{ticket.workOrderId}</span>
                        <span
                          className={`text-[10px] font-mono px-2 py-0.5 rounded border font-semibold ${
                            ticket.delayed
                              ? 'bg-purple-100 text-purple-900 border-purple-300'
                              : 'bg-amber-100 text-amber-900 border-amber-300'
                          }`}
                        >
                          {ticket.delayed ? `DIFERIDA (x-delay ${ticket.delayMillis}ms)` : 'INMEDIATA'}
                        </span>
                      </div>
                      <p className="text-[#4A3F35] mt-1">{ticket.executionSummary}</p>
                    </div>
                    <div className="text-right font-mono text-[11px] text-[#6E6256] shrink-0">
                      <div>Op: {ticket.operatorId}</div>
                      <div>{new Date(ticket.completedAt).toLocaleTimeString()}</div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>
        </div>

        <section className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-2xl p-5 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-[#EDE4D3]">
            <div className="flex items-center gap-2.5">
              <Database className="w-5 h-5 text-[#8C6239]" />
              <div>
                <h2 className="text-base font-bold text-[#2C2523]">
                  Registro Histórico de Auditoría Transaccional en PostgreSQL (<code className="text-[#8C6239]">operator_decision_audit</code>)
                </h2>
                <p className="text-xs text-[#6E6256]">
                  Persistencia ACID verificada antes del despacho a RabbitMQ • Respaldo diario cifrado en <code className="text-[#8C6239]">/var/backups/scada-postgres</code>.
                </p>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-3 text-xs">
              <div className="flex items-center gap-2">
                <span className="text-[#6E6256] font-medium">Sensor:</span>
                <select
                  value={sensorFilter}
                  onChange={(e) => setSensorFilter(e.target.value)}
                  className="bg-[#FAF6EE] border border-[#D8C7AE] rounded-lg px-2.5 py-1 text-[#2C2523] font-mono font-semibold"
                >
                  <option value="ALL">TODOS ({auditLogs.length})</option>
                  {sensors.map((s) => (
                    <option key={s.sensorId} value={s.sensorId}>
                      {s.sensorId}
                    </option>
                  ))}
                </select>
              </div>

              <button
                onClick={handleExportAuditCsv}
                disabled={auditLogs.length === 0}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#F4ECE0] hover:bg-[#E9DFCE] text-[#6B4A28] border border-[#D8C7AE] font-semibold transition disabled:opacity-40"
              >
                <Download className="w-3.5 h-3.5" />
                Exportar Acta CSV
              </button>
            </div>
          </div>

          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-[#E5DCCB] bg-[#FAF6EE] text-[#5C5146] font-mono uppercase text-[11px]">
                  <th className="py-2.5 px-3">ID</th>
                  <th className="py-2.5 px-3">Alerta</th>
                  <th className="py-2.5 px-3">Sensor</th>
                  <th className="py-2.5 px-3">Acción Elegida</th>
                  <th className="py-2.5 px-3">Operador Auth</th>
                  <th className="py-2.5 px-3">Destino RabbitMQ</th>
                  <th className="py-2.5 px-3">x-delay (ms)</th>
                  <th className="py-2.5 px-3">Notas</th>
                  <th className="py-2.5 px-3">Fecha / Hora</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#EDE4D3]">
                {filteredAudits.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="py-8 text-center text-[#6E6256]">
                      No hay registros de auditoría en PostgreSQL para el filtro seleccionado.
                    </td>
                  </tr>
                ) : (
                  filteredAudits.map((row) => (
                    <tr key={row.id} className="hover:bg-[#FAF6EE] transition">
                      <td className="py-2.5 px-3 font-mono font-bold text-[#8C6239]">#{row.id}</td>
                      <td className="py-2.5 px-3 font-mono text-[#4A3F35]">{row.alertId}</td>
                      <td className="py-2.5 px-3 font-mono font-bold text-[#2C2523]">{row.sensorId}</td>
                      <td className="py-2.5 px-3">
                        <span
                          className={`font-mono text-[11px] px-2 py-0.5 rounded border font-semibold ${
                            ACTION_LABELS[row.chosenAction]?.badgeClass ??
                            'bg-[#EDE5D5] text-[#2C2523] border-[#D5C7B0]'
                          }`}
                        >
                          {row.chosenAction}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 font-mono font-semibold text-[#6B4A28]">{row.operatorId}</td>
                      <td className="py-2.5 px-3 font-mono text-[#4A3F35]">{row.routingTarget}</td>
                      <td className="py-2.5 px-3 font-mono text-[#4A3F35]">{row.delayMillis} ms</td>
                      <td className="py-2.5 px-3 text-[#4A3F35] max-w-xs truncate">{row.notes}</td>
                      <td className="py-2.5 px-3 font-mono text-[#6E6256]">
                        {new Date(row.recordedAt).toLocaleString()}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  );
}
