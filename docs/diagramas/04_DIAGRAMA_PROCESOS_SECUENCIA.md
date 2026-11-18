# 04. Diagramas de Procesos y Secuencia — Flujo de Datos, Decisiones y Auditoría

Para explicar con total claridad el funcionamiento de punta a punta, los procesos se dividen en **3 secuencias especializadas** que corresponden exactamente a los objetivos de aprendizaje del caso de estudio.

---

## 1. Proceso A: Flujo de Datos Masivos y Detección Stateful en Apache Kafka

Demuestra cómo se garantiza el **orden cronológico estricto por sensor** utilizando `sensorId` como **Key de particionamiento en Kafka** y cómo los patrones **Strategy** y **State** evitan la tormenta de alertas.

```mermaid
sequenceDiagram
    autonumber
    participant SIM as 📡 sensor_producer
    participant K_DATA as ⚡ Kafka Topic: sensor_data (3 Particiones)
    participant PMON as 🖥️ plant_monitor_backend
    participant DET as 🧠 alert_detector (Ventana Móvil)
    participant K_ALERT as ⚡ Kafka Topics: alerts_warning / alerts_critical
    actor UI as 💻 Gemelo Digital 3D (React)

    SIM->>K_DATA: Publica VibrationReadingRecord (Key = "sensor_A", Valor = 96.4 mm/s)
    Note over K_DATA: Kafka asigna siempre "sensor_A" a la misma Partición (Orden Garantizado)

    par Actualización Visual del Gemelo 3D
        K_DATA->>PMON: Consume lectura ordenada
        PMON->>UI: Envía trama STOMP (/topic/plant-status)
        UI->>UI: Ilumina Piso 1 en Rojo + Activa onda expansiva en sensor_A
    and Análisis Stateful en Ventana Móvil
        K_DATA->>DET: Consume lectura en SensorStreamWindow("sensor_A")
        DET->>DET: 1. Agrega lectura a Deque (Ventana Móvil)
        DET->>DET: 2. Ejecuta Strategy (ThresholdSlidingWindowStrategy) -> CRITICAL
        DET->>DET: 3. Evalúa Patrón State (NormalSensorState -> CriticalSensorState)
        alt Ocurrió Transición de Estado (ej. NORMAL a CRITICAL)
            DET->>K_ALERT: Publica IndustrialAlert en topic 'alerts_critical'
        else Sigue en el mismo estado (CRITICAL -> CRITICAL)
            DET->>DET: Suprime emisión repetida (Previene tormenta de alertas)
        end
    end
```

---

## 2. Proceso B: Enrutamiento de Alertas y Generación de Opciones (`alert_router` + RabbitMQ Fanout)

Demuestra el puente entre el bus analítico (**Apache Kafka**) y el bus de decisiones operativas (**RabbitMQ**), enriqueciendo la alerta con opciones de acción para el humano.

```mermaid
sequenceDiagram
    autonumber
    participant K_ALERT as ⚡ Kafka (alerts_critical / alerts_warning)
    participant ROUT as 🔀 alert_router
    participant FANOUT as 🐇 RabbitMQ Fanout: human_alerts
    participant SNS as ☁️ Amazon SNS (scada-iot-alertas-ucc)
    participant OCON as 🚨 operator_console_backend
    actor OP as 👷 Operador en Consola SCADA

    K_ALERT->>ROUT: Consume IndustrialAlert (alertId, sensorId, severity, triggerValue)
    ROUT->>ROUT: AlertOptionGeneratorDomainService genera opciones según severidad
    Note right of ROUT: Opciones: APAGADO_INMEDIATO, PROGRAMAR_MANTENIMIENTO_AHORA,\nRECONOCER_Y_ESPERAR_24H, IGNORAR_10_MINUTOS
    ROUT->>FANOUT: Publica HumanActionRequest (JSON con IDs planos @JsonValue)
    opt Si severity == CRITICAL (con Cooldown de 60s)
        ROUT--)SNS: Publica Boletín de Alerta Temprana ISO 10816-3 al Correo Institucional
    end
    FANOUT->>OCON: Enruta copia a 'human_alerts_console_queue'
    OCON->>OP: Push WebSocket STOMP (/topic/operator-alerts)
    OP->>OP: Visualiza tarjeta de alerta y botones de maniobra interactivos
```

---

## 3. Proceso C: Decisión Humana, Auditoría ACID (`afterCommit`), Retardo `x-delay` y Ejecución en Workers

Demuestra la **consistencia transaccional** entre PostgreSQL y RabbitMQ, el funcionamiento del plugin **`rabbitmq_delayed_message_exchange`** y la distribución de carga entre las **2 réplicas** de cada worker.

```mermaid
sequenceDiagram
    autonumber
    actor OP as 👷 Operador Autenticado (operator / admin)
    participant DISP as ⚖️ action_dispatcher
    participant PG as 🗄️ PostgreSQL 16 (operator_decision_audit)
    participant RMQ_IMM as 🐇 Exchange: immediate_actions
    participant RMQ_DLY as ⏳ Exchange: delayed_actions (x-delayed-message)
    participant ACT as 🛑 actuator_worker (2 Réplicas)
    participant MAINT as 🔧 maintenance_worker (2 Réplicas)
    participant SNS as ☁️ Amazon SNS (Correo Institucional)

    OP->>DISP: POST /api/dispatcher/decide (Basic Auth + Acción + Notas)
    DISP->>DISP: Extrae operatorId real del SecurityContext (Principal)
    DISP->>PG: BEGIN TX -> INSERT INTO operator_decision_audit -> COMMIT
    PG-->>DISP: Confirmación exitosa (auditRecordId = #N)

    Note over DISP: Se dispara TransactionSynchronization.afterCommit()

    alt Opción 1: APAGADO_INMEDIATO
        DISP->>RMQ_IMM: Publica con routingKey 'critical.shutdown'
        RMQ_IMM->>ACT: Entrega en 'critical_actions_queue' (Balanceo Round-Robin)
        ACT->>ACT: Cambia máquina a EMERGENCY_HALTED
    else Opción 2: PROGRAMAR_MANTENIMIENTO_AHORA
        DISP->>RMQ_IMM: Publica con routingKey 'maintenance.immediate'
        RMQ_IMM->>MAINT: Entrega inmediata en 'maintenance_queue'
        MAINT->>MAINT: Crea Orden de Trabajo Inmediata (COMPLETED)
    else Opción 3: RECONOCER_Y_ESPERAR_24H (Tarea Diferida)
        DISP->>RMQ_DLY: Publica con cabecera 'x-delay: 10000' (10s simulación de 24h)
        Note over RMQ_DLY: El Broker RabbitMQ retiene el mensaje en memoria/disco durante x-delay
        RMQ_DLY->>MAINT: Al expirar el tiempo, libera hacia 'maintenance_queue'
        MAINT->>MAINT: Crea Orden de Trabajo Diferida (DELAYED_COMPLETED)
    else Opción 4: IGNORAR_10_MINUTOS
        Note over DISP: Queda auditado formalmente en PostgreSQL sin enviar comando a los workers
    end

    DISP--)SNS: Envía Acta Oficial de Resolución ISO 10816-3 al correo del Ingeniero
    DISP-->>OP: HTTP 200 OK + Elimina alerta resuelta de operator_console_backend
```
