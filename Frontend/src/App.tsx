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
    badgeClass: 'bg-red-500/20 text-red-300 border-red-500/40',
    btnClass: 'bg-red-600 hover:bg-red-500 text-white border-red-400/50 shadow-lg shadow-red-950/50'
  },
  IGNORAR_10_MINUTOS: {
    label: '⏱️ IGNORAR 10 MINUTOS (Bajo Riesgo)',
    badgeClass: 'bg-slate-700/40 text-slate-300 border-slate-600',
    btnClass: 'bg-slate-800 hover:bg-slate-700 text-slate-200 border-slate-600'
  },
  PROGRAMAR_MANTENIMIENTO_AHORA: {
    label: '🔧 PROGRAMAR MANTENIMIENTO AHORA',
    badgeClass: 'bg-amber-500/20 text-amber-300 border-amber-500/40',
    btnClass: 'bg-amber-600 hover:bg-amber-500 text-slate-950 font-bold border-amber-400/50'
  },
  RECONOCER_Y_ESPERAR_24H: {
    label: '⏳ RECONOCER Y ESPERAR 24H (x-delay 10s)',
    badgeClass: 'bg-purple-500/20 text-purple-300 border-purple-500/40',
    btnClass: 'bg-purple-600 hover:bg-purple-500 text-white border-purple-400/50'
  }
};

const HARDENING_LAYERS = [
  {
    layer: '1. Reverse Proxy & Dominio Propio',
    tech: 'Nginx + TLS 1.2/1.3 + HSTS + Rate Limit',
    detail: 'Dominio scada-ucc-alejandro.77.113.90.86.sslip.io. Todos los microservicios escuchan en 127.0.0.1 ocultando puertos internos.'
  },
  {
    layer: '2. Blindaje SSH & Cuentas',
    tech: '/etc/ssh/sshd_config.d/01-hardening.conf',
    detail: 'PermitRootLogin no, PasswordAuthentication no, MaxAuthTries 3, LoginGraceTime 30, Banner legal /etc/issue.net.'
  },
  {
    layer: '3. Autenticación PAM & Sudo',
    tech: 'pwquality + faillock + /etc/sudoers.d/99-hardening',
    detail: 'Defaults use_pty, timestamp_timeout=5, passwd_tries=3 y bloqueo tras 5 intentos fallidos (15 min).'
  },
  {
    layer: '4. Firewall Host & IPS Fail2ban',
    tech: 'UFW / nftables + Fail2ban (sshd, nginx, recidive)',
    detail: 'Política deny incoming por defecto. Bloqueo automático incremental de IPs agresivas y escáneres.'
  },
  {
    layer: '5. Hardening del Kernel Linux',
    tech: '/etc/sysctl.d/99-hardening.conf + Coredump Off',
    detail: 'ASLR completo (randomize_va_space=2), tcp_syncookies=1, rp_filter=1, kptr_restrict=2, dmesg_restrict=1.'
  },
  {
    layer: '6. Auditoría Forense & Monitoreo',
    tech: 'auditd + check-resources.sh + Lynis + sysstat',
    detail: 'Reglas inmutables vigilando /etc/passwd, /etc/shadow, /etc/sudoers y cron de vigilancia de CPU/RAM/Disco cada 5m.'
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
    ntfyTopic: 'scada-ucc-alejandro-573154613867',
    ntfySubscribeUrl: 'https://ntfy.sh/scada-ucc-alejandro-573154613867',
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
          : 'Ahora solo se emitirán alertas cuando presiones los botones manuales en cada sensor.'
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
        title: `Decisión Auditada en PostgreSQL (#${result.auditRecordId}), Despachada a RabbitMQ y Notificada`,
        detail: `Sensor: ${result.sensorId} | Acción: ${result.actionDispatched} | Notificado a ${notifConfig.phone} y ${notifConfig.email}`
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
        title: `Alerta Multicanal Despachada (${rec.notificationId})`,
        detail: `Enviada a ${rec.recipient} — Estado: ${rec.deliveryStatus}`
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
      `🚨 *ALERTA SCADA IoT INDUSTRIAL (UCC)*\n` +
      `• *Alerta:* ${alert.alertId}\n` +
      `• *Sensor:* ${alert.sensorId}\n` +
      `• *Severidad:* ${alert.severity} (${Number(alert.triggerValue).toFixed(2)} mm/s)\n` +
      `• *Detalle:* ${alert.description}\n` +
      `• *Opciones:* ${alert.availableOptions.join(', ')}`;
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
        card: 'border-rose-500/80 bg-rose-950/30 shadow-lg shadow-rose-950/30',
        badge: 'bg-rose-600 text-white',
        bar: 'bg-rose-500',
        label: 'DETENIDA (EMERGENCIA)'
      };
    }
    switch (severity) {
      case 'CRITICAL':
        return {
          card: 'border-red-500/70 bg-red-950/20 shadow-lg shadow-red-950/20',
          badge: 'bg-red-500/20 text-red-300 border border-red-500/40',
          bar: 'bg-red-500',
          label: 'CRÍTICO (>90 mm/s)'
        };
      case 'WARNING':
        return {
          card: 'border-amber-500/70 bg-amber-950/20 shadow-lg shadow-amber-950/20',
          badge: 'bg-amber-500/20 text-amber-300 border border-amber-500/40',
          bar: 'bg-amber-400',
          label: 'ADVERTENCIA (>75 x3)'
        };
      default:
        return {
          card: 'border-slate-800 bg-slate-900/70 hover:border-slate-700',
          badge: 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30',
          bar: 'bg-emerald-400',
          label: 'OPERACIÓN NORMAL'
        };
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      {/* Top Navbar */}
      <header className="sticky top-0 z-30 border-b border-slate-800/80 bg-slate-950/90 backdrop-blur-md px-6 py-3.5">
        <div className="max-w-[1600px] mx-auto flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-cyan-500/15 border border-cyan-500/40 flex items-center justify-center text-cyan-400">
              <Cpu className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-bold tracking-tight text-white">
                  SCADA IoT Industrial — Mantenimiento Predictivo Interactivo
                </h1>
                <span className="text-[11px] font-mono uppercase px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                  AWS EC2 + Hardening OS
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Kafka (Stateful) • RabbitMQ (Fanout + Delayed x-delay) • PostgreSQL (ACID) • Alertas a {notifConfig.phone} y {notifConfig.email}
              </p>
            </div>
          </div>

          {/* Controles de Estado STOMP + Pausa Simulación + Sesión Auth */}
          <div className="flex flex-wrap items-center gap-2.5">
            <button
              onClick={handleToggleSimulation}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-semibold transition ${
                simState.enabled
                  ? 'bg-cyan-950/50 border-cyan-500/40 text-cyan-300 hover:bg-cyan-900/50'
                  : 'bg-amber-950/50 border-amber-500/40 text-amber-300 hover:bg-amber-900/50'
              }`}
              title="Pausa o reanuda la emisión aleatoria cada 12s en sensor_producer"
            >
              {simState.enabled ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
              <span>{simState.enabled ? 'Simulación Auto (12s): ON' : 'Simulación Auto: PAUSADA'}</span>
            </button>

            <div
              className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-mono ${
                plantWs.connected
                  ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300'
                  : 'bg-amber-950/40 border-amber-500/40 text-amber-300'
              }`}
            >
              <Radio className="w-3.5 h-3.5 animate-pulse" />
              <span>WS Planta: {plantWs.connected ? 'ONLINE' : 'SYNC'}</span>
            </div>

            <div
              className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-mono ${
                alertsWs.connected
                  ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300'
                  : 'bg-amber-950/40 border-amber-500/40 text-amber-300'
              }`}
            >
              <Radio className="w-3.5 h-3.5 animate-pulse" />
              <span>WS Alertas: {alertsWs.connected ? 'ONLINE' : 'SYNC'}</span>
            </div>

            <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 rounded-lg px-3 py-1.5 text-xs">
              <ShieldCheck className="w-4 h-4 text-cyan-400" />
              <select
                value={credentials.username}
                onChange={(e) => {
                  const user = e.target.value;
                  setCredentials({
                    username: user,
                    password: user === 'admin' ? 'admin123' : 'operator123'
                  });
                }}
                className="bg-slate-800 text-cyan-300 font-mono rounded px-2 py-0.5 border border-slate-700 focus:outline-none"
              >
                <option value="operator">operator (ROLE_OPERATOR)</option>
                <option value="admin">admin (ROLE_ADMIN)</option>
              </select>
            </div>

            <button
              onClick={refreshAllData}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium border border-slate-700 transition"
            >
              <RefreshCw className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </header>

      {/* Banner de Respuesta Real del Backend */}
      {feedbackBanner && (
        <div className="max-w-[1600px] w-full mx-auto px-6 pt-4">
          <div
            className={`flex items-center justify-between gap-4 px-4 py-3 rounded-xl border text-sm ${
              feedbackBanner.type === 'success'
                ? 'bg-emerald-950/50 border-emerald-500/50 text-emerald-200'
                : feedbackBanner.type === 'error'
                ? 'bg-red-950/60 border-red-500/60 text-red-200'
                : 'bg-cyan-950/50 border-cyan-500/50 text-cyan-200'
            }`}
          >
            <div className="flex items-center gap-3">
              {feedbackBanner.type === 'success' && <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />}
              {feedbackBanner.type === 'error' && <XCircle className="w-5 h-5 text-red-400 shrink-0" />}
              {feedbackBanner.type === 'info' && <Zap className="w-5 h-5 text-cyan-400 shrink-0" />}
              <div>
                <p className="font-semibold">{feedbackBanner.title}</p>
                <p className="text-xs opacity-90 font-mono">{feedbackBanner.detail}</p>
              </div>
            </div>
            <button
              onClick={() => setFeedbackBanner(null)}
              className="text-xs px-2.5 py-1 rounded bg-slate-900/60 hover:bg-slate-900 text-slate-300"
            >
              Cerrar
            </button>
          </div>
        </div>
      )}

      {/* Main Content Grid */}
      <main className="max-w-[1600px] w-full mx-auto px-6 py-6 flex-1 space-y-6">
        {/* KPIs Superiores */}
        <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
            <div>
              <p className="text-xs text-slate-400 uppercase">Sensores Normales</p>
              <p className="text-2xl font-bold text-emerald-400 mt-1">{counts.normal} / 10</p>
            </div>
            <Activity className="w-8 h-8 text-emerald-500/40" />
          </div>

          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
            <div>
              <p className="text-xs text-slate-400 uppercase">En Advertencia</p>
              <p className="text-2xl font-bold text-amber-400 mt-1">{counts.warning}</p>
            </div>
            <AlertTriangle className="w-8 h-8 text-amber-500/40" />
          </div>

          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
            <div>
              <p className="text-xs text-slate-400 uppercase">Críticos / Detenidos</p>
              <p className="text-2xl font-bold text-red-400 mt-1">{counts.critical}</p>
            </div>
            <AlertOctagon className="w-8 h-8 text-red-500/40" />
          </div>

          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
            <div>
              <p className="text-xs text-slate-400 uppercase">Alertas Activas</p>
              <p className="text-2xl font-bold text-cyan-400 mt-1">{alerts.length}</p>
            </div>
            <Flame className="w-8 h-8 text-cyan-500/40" />
          </div>

          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
            <div>
              <p className="text-xs text-slate-400 uppercase">Auditorías PostgreSQL</p>
              <p className="text-2xl font-bold text-purple-400 mt-1">{auditLogs.length}</p>
            </div>
            <Database className="w-8 h-8 text-purple-500/40" />
          </div>
        </div>

        {/* Bloque Principal: Mapa de Sensores SCADA (Izquierda) + Consola de Decisiones del Operador (Derecha) */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Columna Izquierda (7 cols): Mapa de Estado General de la Planta (10 Sensores) */}
          <section className="lg:col-span-7 bg-slate-900/60 border border-slate-800 rounded-2xl p-5 flex flex-col">
            <div className="flex flex-wrap items-center justify-between gap-2 pb-4 border-b border-slate-800">
              <div>
                <h2 className="text-base font-bold text-white flex items-center gap-2">
                  <Activity className="w-5 h-5 text-cyan-400" />
                  Mapa de Estado Materializado de la Planta (10 Sensores — Kafka Stateful)
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Ciclo realista de 12s (97% normal) para evitar saturación. Usa los botones de cada sensor para disparar alertas de prueba al instante.
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
                          <span className="font-mono font-bold text-sm text-white">{sensor.sensorId}</span>
                          <span className="text-[11px] font-mono text-slate-400">[{sensor.operationalStatus}]</span>
                        </div>
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${style.badge}`}>
                          {style.label}
                        </span>
                      </div>

                      <div className="mt-3 flex items-baseline justify-between">
                        <div>
                          <span className="text-2xl font-mono font-extrabold text-white">
                            {sensor.lastVibration.toFixed(2)}
                          </span>
                          <span className="text-xs text-slate-400 ml-1">mm/s RMS</span>
                        </div>
                        <span className="text-[11px] text-slate-400 font-mono">
                          Umbral: 75 / 90 mm/s
                        </span>
                      </div>

                      <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden mt-2">
                        <div
                          className={`h-full transition-all duration-300 ${style.bar}`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>

                      <p className="text-xs text-slate-300 mt-2.5 line-clamp-2 min-h-[2rem]">
                        {sensor.statusDescription}
                      </p>
                    </div>

                    <div className="mt-3 pt-2.5 border-t border-slate-800/80 flex flex-wrap items-center gap-1.5">
                      <button
                        onClick={() => handleInjectSpike(sensor.sensorId, 96.4, 1)}
                        disabled={isHalted}
                        className="flex-1 text-[11px] font-semibold px-2 py-1.5 rounded bg-red-500/20 hover:bg-red-500/30 text-red-300 border border-red-500/30 disabled:opacity-40 transition"
                      >
                        ⚡ Pico &gt;90
                      </button>

                      <button
                        onClick={() => handleInjectSpike(sensor.sensorId, 82.0, 3)}
                        disabled={isHalted}
                        className="flex-1 text-[11px] font-semibold px-2 py-1.5 rounded bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border border-amber-500/30 disabled:opacity-40 transition"
                      >
                        ⚠️ Ráfaga 3x &gt;75
                      </button>

                      {(isHalted || sensor.severity !== 'NORMAL') && (
                        <button
                          onClick={() => handleResetMachine(sensor.sensorId)}
                          className="text-[11px] font-semibold px-2.5 py-1.5 rounded bg-emerald-600 hover:bg-emerald-500 text-white flex items-center gap-1 transition"
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

          {/* Columna Derecha (5 cols): Consola Interactiva de Decisiones del Operador */}
          <section className="lg:col-span-5 bg-slate-900/60 border border-slate-800 rounded-2xl p-5 flex flex-col">
            <div className="pb-4 border-b border-slate-800 flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold text-white flex items-center gap-2">
                  <AlertOctagon className="w-5 h-5 text-amber-400" />
                  Consola Interactiva del Operador (RabbitMQ Fanout)
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Opciones dinámicas desde <code className="text-amber-300">alert_router</code> con notificación automática a móvil y correo.
                </p>
              </div>
              <span className="px-2.5 py-1 rounded-full text-xs font-mono font-bold bg-slate-800 text-cyan-300 border border-slate-700">
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
                      className="rounded-xl bg-purple-950/40 border border-purple-500/40 p-3 text-xs"
                    >
                      <div className="flex items-center justify-between text-purple-200 font-mono">
                        <span className="flex items-center gap-1.5 font-semibold">
                          <Clock className="w-4 h-4 text-purple-400 animate-spin" />
                          RabbitMQ Delayed Exchange (x-delay) — {item.sensorId}
                        </span>
                        <span>{(item.remainingMs / 1000).toFixed(1)}s restantes</span>
                      </div>
                      <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden mt-2">
                        <div
                          className="h-full bg-purple-400 transition-all duration-200"
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
                <div className="h-64 flex flex-col items-center justify-center text-center border border-dashed border-slate-800 rounded-xl p-6 text-slate-400">
                  <CheckCircle2 className="w-10 h-10 text-emerald-500/60 mb-2" />
                  <p className="text-sm font-medium text-slate-300">Sin alertas pendientes de decisión humana</p>
                  <p className="text-xs text-slate-500 mt-1">
                    Haz clic en &ldquo;⚡ Pico &gt;90&rdquo; o &ldquo;⚠️ Ráfaga 3x &gt;75&rdquo; en cualquier sensor para disparar el flujo completo.
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
                          ? 'bg-red-950/25 border-red-500/50'
                          : 'bg-amber-950/25 border-amber-500/50'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <div className="flex items-center gap-2">
                            <span
                              className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded ${
                                isCritical ? 'bg-red-500 text-white' : 'bg-amber-400 text-slate-950'
                              }`}
                            >
                              {alert.severity}
                            </span>
                            <span className="font-mono font-bold text-sm text-white">
                              {alert.sensorId}
                            </span>
                            <span className="font-mono text-xs text-slate-400">
                              ({alert.alertId})
                            </span>
                          </div>
                          <p className="text-xs text-slate-200 mt-1.5">{alert.description}</p>
                        </div>

                        <div className="text-right shrink-0">
                          <span className="text-sm font-mono font-bold text-white">
                            {Number(alert.triggerValue).toFixed(2)} mm/s
                          </span>
                          <p className="text-[10px] text-slate-400 font-mono">
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
                          placeholder="Nota de auditoría opcional para PostgreSQL..."
                          className="w-full text-xs bg-slate-950/80 border border-slate-700/80 rounded-lg px-3 py-1.5 text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-cyan-500"
                        />
                      </div>

                      <div className="mt-3 flex flex-col gap-2">
                        {alert.availableOptions.map((option) => {
                          const meta = ACTION_LABELS[option] ?? {
                            label: option,
                            badgeClass: 'bg-slate-800 text-slate-200',
                            btnClass: 'bg-cyan-600 hover:bg-cyan-500 text-white'
                          };
                          return (
                            <button
                              key={option}
                              disabled={isSubmitting}
                              onClick={() => handleDecisionSubmit(alert, option)}
                              className={`w-full py-2 px-3 rounded-lg text-xs font-semibold border transition flex items-center justify-between ${meta.btnClass} disabled:opacity-50`}
                            >
                              <span>{meta.label}</span>
                              <span className="font-mono text-[10px] opacity-80">{option}</span>
                            </button>
                          );
                        })}

                        <div className="flex items-center justify-between pt-1">
                          <a
                            href={buildAlertWhatsAppUrl(alert)}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1.5 text-[11px] font-medium text-emerald-400 hover:text-emerald-300"
                          >
                            <MessageSquare className="w-3.5 h-3.5" />
                            Enviar a WhatsApp ({notifConfig.phone})
                          </a>

                          <button
                            onClick={() => handleDismissOnly(alert.alertId)}
                            className="text-[11px] text-slate-400 hover:text-slate-200"
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

        {/* NUEVA SECCIÓN PLUS: Centro de Notificaciones Multicanal (Móvil / WhatsApp / Email) + Hardening Ubuntu Server */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Panel Notificaciones (6 cols) */}
          <section className="lg:col-span-6 bg-slate-900/60 border border-slate-800 rounded-2xl p-5 flex flex-col justify-between">
            <div>
              <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-slate-800">
                <div className="flex items-center gap-2">
                  <Bell className="w-5 h-5 text-emerald-400" />
                  <div>
                    <h3 className="text-sm font-bold text-white">
                      Centro de Alertas Multicanal (Móvil / WhatsApp / Correo Institucional)
                    </h3>
                    <p className="text-xs text-slate-400">
                      Despacho automático en alertas críticas (cooldown 60s) y resoluciones del operador.
                    </p>
                  </div>
                </div>

                <button
                  onClick={handleSendTestNotification}
                  disabled={sendingTestNotif}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold transition disabled:opacity-50"
                >
                  <Send className="w-3.5 h-3.5" />
                  {sendingTestNotif ? 'Enviando...' : 'Probar Alerta en Vivo'}
                </button>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-4">
                <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-3 text-xs">
                  <div className="flex items-center gap-1.5 text-cyan-400 font-semibold">
                    <Mail className="w-4 h-4" />
                    <span>Correo Institucional</span>
                  </div>
                  <p className="font-mono text-slate-200 mt-1 break-all">{notifConfig.email}</p>
                </div>

                <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-3 text-xs">
                  <div className="flex items-center gap-1.5 text-emerald-400 font-semibold">
                    <Smartphone className="w-4 h-4" />
                    <span>Móvil / WhatsApp</span>
                  </div>
                  <p className="font-mono text-slate-200 mt-1">{notifConfig.phone}</p>
                </div>

                <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-3 text-xs">
                  <div className="flex items-center justify-between text-purple-400 font-semibold">
                    <span>Push Móvil (ntfy.sh)</span>
                    <ExternalLink className="w-3.5 h-3.5" />
                  </div>
                  <a
                    href={notifConfig.ntfySubscribeUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="font-mono text-purple-300 hover:underline mt-1 block truncate"
                  >
                    {notifConfig.ntfyTopic}
                  </a>
                </div>
              </div>

              <div className="mt-4 max-h-48 overflow-y-auto space-y-2">
                {notifHistory.length === 0 ? (
                  <p className="text-xs text-slate-500 py-6 text-center">
                    Haz clic en &ldquo;Probar Alerta en Vivo&rdquo; o resuelve una alerta para ver el historial de despacho.
                  </p>
                ) : (
                  notifHistory.map((n) => (
                    <div
                      key={n.notificationId}
                      className="rounded-lg bg-slate-950/80 border border-slate-800 p-2.5 text-xs flex items-center justify-between gap-3"
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-mono font-bold text-emerald-400">{n.notificationId}</span>
                          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-cyan-300">
                            {n.deliveryStatus}
                          </span>
                        </div>
                        <p className="text-slate-200 font-medium truncate mt-0.5">{n.subject}</p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <a
                          href={n.whatsappDirectUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="px-2 py-1 rounded bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border border-emerald-500/30 font-semibold text-[11px]"
                        >
                          WhatsApp
                        </a>
                        <span className="font-mono text-[10px] text-slate-400">
                          {new Date(n.sentAt).toLocaleTimeString()}
                        </span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </section>

          {/* Panel Hardening Ubuntu Server 24.04 LTS en AWS EC2 (6 cols) */}
          <section className="lg:col-span-6 bg-slate-900/60 border border-slate-800 rounded-2xl p-5">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <Shield className="w-5 h-5 text-cyan-400" />
                <div>
                  <h3 className="text-sm font-bold text-white">
                    Hardening de Ubuntu Server 24.04 LTS en AWS EC2 (Defensa en Profundidad)
                  </h3>
                  <p className="text-xs text-slate-400">
                    Capas de seguridad de Sistemas Operativos activas en el servidor de producción
                  </p>
                </div>
              </div>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-mono font-bold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                <Lock className="w-3 h-3" /> 6 Capas Activas
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 mt-3.5">
              {HARDENING_LAYERS.map((item) => (
                <div
                  key={item.layer}
                  className="rounded-xl bg-slate-950/70 border border-slate-800/90 p-3 text-xs"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-cyan-300">{item.layer}</span>
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                  </div>
                  <p className="font-mono text-[10px] text-purple-300 mt-0.5">{item.tech}</p>
                  <p className="text-slate-400 text-[11px] mt-1 leading-relaxed">{item.detail}</p>
                </div>
              ))}
            </div>
          </section>
        </div>

        {/* Sección Inferior 1: Ejecución en Workers Concurrentes */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <section className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <Power className="w-5 h-5 text-red-400" />
                <div>
                  <h3 className="text-sm font-bold text-white">
                    Actuator Workers — Paradas de Emergencia y Reactivaciones (2 Réplicas)
                  </h3>
                  <p className="text-xs text-slate-400">
                    Cola RabbitMQ: <code className="text-red-300">critical_actions_queue</code> (127.0.0.1:8091-8092)
                  </p>
                </div>
              </div>
              <span className="text-xs font-mono text-slate-400">{actuatorLogs.length} eventos</span>
            </div>

            <div className="mt-3 max-h-60 overflow-y-auto space-y-2">
              {actuatorLogs.length === 0 ? (
                <p className="text-xs text-slate-500 py-8 text-center">
                  Aún no se han ejecutado comandos de corte de energía en actuator_worker.
                </p>
              ) : (
                actuatorLogs.map((log) => (
                  <div
                    key={log.commandId}
                    className="rounded-lg bg-slate-950/70 border border-slate-800 p-3 text-xs flex items-center justify-between gap-3"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono font-bold text-white">{log.sensorId}</span>
                        <span className="font-mono text-[11px] text-slate-400">{log.commandId}</span>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-red-500/20 text-red-300 border border-red-500/30">
                          {log.previousState} → {log.currentState}
                        </span>
                      </div>
                      <p className="text-slate-300 mt-1">{log.reason}</p>
                    </div>
                    <div className="text-right font-mono text-[11px] text-slate-400 shrink-0">
                      <div>Op: {log.operatorId}</div>
                      <div>{new Date(log.executedAt).toLocaleTimeString()}</div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>

          <section className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <Wrench className="w-5 h-5 text-amber-400" />
                <div>
                  <h3 className="text-sm font-bold text-white">
                    Maintenance Workers — Órdenes Inmediatas y Diferidas x-delay (2 Réplicas)
                  </h3>
                  <p className="text-xs text-slate-400">
                    Cola RabbitMQ: <code className="text-amber-300">maintenance_queue</code> + <code className="text-purple-300">delayed_actions_exchange</code> (127.0.0.1:8093-8094)
                  </p>
                </div>
              </div>
              <span className="text-xs font-mono text-slate-400">{maintenanceTickets.length} órdenes</span>
            </div>

            <div className="mt-3 max-h-60 overflow-y-auto space-y-2">
              {maintenanceTickets.length === 0 ? (
                <p className="text-xs text-slate-500 py-8 text-center">
                  Aún no se han recibido órdenes de trabajo en maintenance_worker.
                </p>
              ) : (
                maintenanceTickets.map((ticket) => (
                  <div
                    key={ticket.workOrderId}
                    className="rounded-lg bg-slate-950/70 border border-slate-800 p-3 text-xs flex items-center justify-between gap-3"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono font-bold text-white">{ticket.sensorId}</span>
                        <span className="font-mono text-[11px] text-slate-400">{ticket.workOrderId}</span>
                        <span
                          className={`text-[10px] font-mono px-2 py-0.5 rounded border ${
                            ticket.delayed
                              ? 'bg-purple-500/20 text-purple-300 border-purple-500/40'
                              : 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                          }`}
                        >
                          {ticket.delayed ? `DIFERIDA (x-delay ${ticket.delayMillis}ms)` : 'INMEDIATA'}
                        </span>
                      </div>
                      <p className="text-slate-300 mt-1">{ticket.executionSummary}</p>
                    </div>
                    <div className="text-right font-mono text-[11px] text-slate-400 shrink-0">
                      <div>Op: {ticket.operatorId}</div>
                      <div>{new Date(ticket.completedAt).toLocaleTimeString()}</div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>
        </div>

        {/* Sección Inferior 2: Tabla de Auditoría Transaccional en PostgreSQL */}
        <section className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5">
          <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-slate-800">
            <div className="flex items-center gap-2.5">
              <Database className="w-5 h-5 text-purple-400" />
              <div>
                <h2 className="text-base font-bold text-white">
                  Registro Histórico de Auditoría Transaccional en PostgreSQL (<code className="text-purple-300">operator_decision_audit</code>)
                </h2>
                <p className="text-xs text-slate-400">
                  Persistencia ACID verificada antes del despacho a RabbitMQ • Autenticado como <code className="text-cyan-300">{credentials.username}</code>.
                </p>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-3 text-xs">
              <div className="flex items-center gap-2">
                <span className="text-slate-400">Sensor:</span>
                <select
                  value={sensorFilter}
                  onChange={(e) => setSensorFilter(e.target.value)}
                  className="bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1 text-slate-200 font-mono"
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
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-purple-600/20 hover:bg-purple-600/30 text-purple-300 border border-purple-500/40 font-semibold transition disabled:opacity-40"
              >
                <Download className="w-3.5 h-3.5" />
                Exportar Acta CSV
              </button>
            </div>
          </div>

          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-slate-800 text-slate-400 font-mono uppercase text-[11px]">
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
              <tbody className="divide-y divide-slate-800/60">
                {filteredAudits.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="py-8 text-center text-slate-500">
                      No hay registros de auditoría en PostgreSQL para el filtro seleccionado.
                    </td>
                  </tr>
                ) : (
                  filteredAudits.map((row) => (
                    <tr key={row.id} className="hover:bg-slate-900/90 transition">
                      <td className="py-2.5 px-3 font-mono font-bold text-purple-300">#{row.id}</td>
                      <td className="py-2.5 px-3 font-mono text-slate-300">{row.alertId}</td>
                      <td className="py-2.5 px-3 font-mono font-bold text-white">{row.sensorId}</td>
                      <td className="py-2.5 px-3">
                        <span
                          className={`font-mono text-[11px] px-2 py-0.5 rounded border ${
                            ACTION_LABELS[row.chosenAction]?.badgeClass ??
                            'bg-slate-800 text-slate-200 border-slate-700'
                          }`}
                        >
                          {row.chosenAction}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 font-mono text-cyan-300">{row.operatorId}</td>
                      <td className="py-2.5 px-3 font-mono text-slate-300">{row.routingTarget}</td>
                      <td className="py-2.5 px-3 font-mono text-slate-300">{row.delayMillis} ms</td>
                      <td className="py-2.5 px-3 text-slate-300 max-w-xs truncate">{row.notes}</td>
                      <td className="py-2.5 px-3 font-mono text-slate-400">
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
