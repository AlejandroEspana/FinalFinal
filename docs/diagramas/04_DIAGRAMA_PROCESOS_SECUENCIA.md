# Diagramas de Procesos y Secuencia — Flujo de Datos, Flujo de Decisiones y Auditoría ACID

Este documento presenta los **Diagramas de Procesos y Secuencia** de punta a punta de la plataforma, mostrando cómo interactúan el **Flujo de Datos Masivos (Apache Kafka)**, el **Flujo de Decisiones y Tareas Diferidas (RabbitMQ)**, la **Persistencia Transaccional (PostgreSQL)**, el **Gemelo Digital 3D** y la **Notificación Cloud (Amazon SNS + Amazon S3)**.

---

## 1. Diagrama de Secuencia E2E: Ingesta, Detección Stateful, Decisión Humana y Despacho

```mermaid
sequenceDiagram
    autonumber
    actor OP as 👷 Operador SCADA (Gemelo 3D)
    participant NGINX as 🔒 Nginx Reverse Proxy (TLS)
    participant PROD as 📡 sensor_producer
    participant KAFKA as ⚡ Apache Kafka (3 Particiones)
    participant PMON as 🖥️ plant_monitor_backend
    participant DET as 🧠 alert_detector (State + Strategy)
    participant ROUT as 🔀 alert_router
    participant RMQ as 🐇 RabbitMQ (Fanout & x-delay)
    participant OCON as 🚨 operator_console_backend
    participant DISP as ⚖️ action_dispatcher (Spring Security)
    participant PG as 🗄️ PostgreSQL 16 (ACID)
    participant WORK as 🔧 actuator / maintenance_worker
    participant SNS as ☁️ Amazon SNS (Email ISO-10816)

    %% Fase 1: Flujo de Datos (Kafka)
    rect rgb(245, 240, 230)
        Note over PROD,DET: FASE 1 — FLUJO DE DATOS E INGESTA STATEFUL (APACHE KAFKA)
        PROD->>KAFKA: Publica lectura en topic 'sensor_data' (Key = sensorId)
        par Actualización de Gemelo 3D
            KAFKA->>PMON: Consume lectura ordenada por partición
            PMON-->>NGINX: Push STOMP WebSocket (/topic/plant-status)
            NGINX-->>OP: Actualiza vibración y color del piso/nodo en Edificio 3D
        and Evaluación en Ventana Móvil Stateful
            KAFKA->>DET: Consume lectura en SensorStreamWindow(sensorId)
            DET->>DET: Evalúa Strategy (>75 mm/s x3 o >90 mm/s) + State Transition
            opt Transición de Estado (ej. NORMAL -> CRITICAL)
                DET->>KAFKA: Publica IndustrialAlert en 'alerts_critical'
            end
        end
    end

    %% Fase 2: Enrutamiento hacia el Humano (RabbitMQ Fanout + SNS)
    rect rgb(254, 243, 199)
        Note over KAFKA,SNS: FASE 2 — GENERACIÓN DE OPCIONES Y ALERTA AL OPERADOR (RABBITMQ FANOUT)
        KAFKA->>ROUT: Consume alerta desde 'alerts_critical' / 'alerts_warning'
        ROUT->>ROUT: Genera opciones dinámicas (AlertOptionGeneratorDomainService)
        ROUT->>RMQ: Publica HumanActionRequest en Fanout Exchange 'human_alerts'
        ROUT--)SNS: Emite Boletín de Alerta Temprana ISO-10816 (si es CRITICAL)
        RMQ->>OCON: Entrega en cola 'human_alerts_console_queue'
        OCON-->>NGINX: Push STOMP WebSocket (/topic/operator-alerts)
        NGINX-->>OP: Muestra alerta activa y botones de maniobra en Consola
    end

    %% Fase 3: Decisión del Operador, Auditoría ACID y Ejecución
    rect rgb(220, 252, 231)
        Note over OP,SNS: FASE 3 — DECISIÓN HUMANA, AUDITORÍA ACID Y DESPACHO (OUTBOX / AFTER-COMMIT)
        OP->>NGINX: POST /api/dispatcher/decide (Basic Auth + Acción + Notas)
        NGINX->>DISP: Reenvía petición autenticada
        DISP->>DISP: Extrae operatorId desde SecurityContext (Principal)
        DISP->>PG: BEGIN TX -> INSERT INTO operator_decision_audit -> COMMIT
        PG-->>DISP: Confirmación ACID (auditRecordId)

        Note right of DISP: Hook TransactionSynchronization.afterCommit()
        alt Acción = APAGADO_INMEDIATO
            DISP->>RMQ: Publica en 'immediate_actions_exchange' (routingKey: critical.shutdown)
            RMQ->>WORK: Consume en 'critical_actions_queue' (actuator_worker)
            WORK->>WORK: Ejecuta corte de energía (EMERGENCY_HALTED)
        else Acción = PROGRAMAR_MANTENIMIENTO_AHORA
            DISP->>RMQ: Publica en 'immediate_actions_exchange' (routingKey: maintenance.immediate)
            RMQ->>WORK: Consume en 'maintenance_queue' (maintenance_worker)
            WORK->>WORK: Genera Orden de Trabajo Inmediata
        else Acción = RECONOCER_Y_ESPERAR_24H (Diferida)
            DISP->>RMQ: Publica en 'delayed_actions_exchange' (Header x-delay: 10000 ms)
            Note over RMQ: Retiene mensaje en plugin x-delayed-message
            RMQ->>WORK: Libera tras expirar x-delay hacia 'maintenance_queue'
            WORK->>WORK: Genera Orden de Trabajo Diferida
        end

        DISP--)SNS: Emite Acta Oficial de Resolución ISO-10816 al Correo Institucional
        DISP-->>NGINX: HTTP 200 OK (DecisionDispatchedResponseDto)
        NGINX-->>OP: Confirma auditoría y limpia alerta de la vista
    end
```

---

## 2. Diagrama de Proceso: Máquina de Estados del Sensor y Reglas deEnrutamiento

```mermaid
flowchart TD
    START(["📡 Nueva Lectura de Vibración\nVibrationReadingRecord (sensorId, mm/s)"]) --> WINDOW["Agregar a Ventana Móvil Stateful\nSensorStreamWindow (Últimas N lecturas)"]

    WINDOW --> CHECK_CRIT{"¿Vibración > 90.0 mm/s?"}
    CHECK_CRIT -->|"Sí"| TRANS_CRIT{"¿Estado actual != CRITICAL?"}
    TRANS_CRIT -->|"Sí (Transición)"| EMIT_CRIT["Cambiar a CriticalSensorState\nEmitir Alerta en 'alerts_critical'"]
    TRANS_CRIT -->|"No (Ya estaba en CRITICAL)"| SUPPRESS["Suprimir emisión duplicada\n(Evita tormenta de alertas)"]

    CHECK_CRIT -->|"No"| CHECK_WARN{"¿3 lecturas consecutivas\n> 75.0 mm/s?"}
    CHECK_WARN -->|"Sí"| TRANS_WARN{"¿Estado actual == NORMAL?"}
    TRANS_WARN -->|"Sí (Transición)"| EMIT_WARN["Cambiar a WarningSensorState\nEmitir Alerta en 'alerts_warning'"]
    TRANS_WARN -->|"No"| SUPPRESS

    CHECK_WARN -->|"No"| NORMALIZE["Mantener / Regresar a NormalSensorState\n(Sin emisión de alerta)"]

    EMIT_CRIT --> ROUTER["alert_router: Generar Opciones"]
    EMIT_WARN --> ROUTER

    ROUTER --> HUMAN{"Decisión del Operador\nen Consola SCADA"}

    HUMAN -->|"APAGADO_INMEDIATO"| TX_AUDIT["1. Guardar en PostgreSQL (ACID)"]
    HUMAN -->|"PROGRAMAR_MANTENIMIENTO_AHORA"| TX_AUDIT
    HUMAN -->|"RECONOCER_Y_ESPERAR_24H"| TX_AUDIT
    HUMAN -->|"IGNORAR_10_MINUTOS"| TX_AUDIT

    TX_AUDIT --> AFTER_COMMIT["2. Hook afterCommit() + Boletín AWS SNS"]

    AFTER_COMMIT -->|"Corte Crítico"| Q_ACT["Cola: critical_actions_queue\n-> actuator_worker (x2)"]
    AFTER_COMMIT -->|"Mantenimiento Inmediato"| Q_MAINT["Cola: maintenance_queue\n-> maintenance_worker (x2)"]
    AFTER_COMMIT -->|"Diferido x-delay"| EX_DELAY["Exchange: delayed_actions_exchange\n(Espera x-delay -> maintenance_queue)"]
    AFTER_COMMIT -->|"Ignorar"| AUDIT_ONLY["Solo Auditoría en PostgreSQL\n(Sin comando a workers)"]
```
