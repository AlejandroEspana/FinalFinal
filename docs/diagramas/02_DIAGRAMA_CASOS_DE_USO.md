# 02. Diagramas de Casos de Uso UML — Plataforma de Mantenimiento Predictivo Interactivo

Para presentar con claridad todas las capacidades del sistema, los **Casos de Uso** se dividen en **3 Subsistemas Funcionales** según los actores que intervienen, acompañados de su matriz de trazabilidad hacia los microservicios.

---

## 1. Subsistema A: Monitoreo en Tiempo Real, Gemelo Digital 3D y Simulación (Apache Kafka)

```mermaid
flowchart LR
    OP(["👷 Operador de Planta\n(ROLE_OPERATOR)"])
    SIM(["📡 Simulador PLC / Sensores\n(sensor_producer)"])

    subgraph SUB_MONITOREO["Subsistema 1: Telemetría Stateful y Gemelo Digital 3D"]
        direction TB
        UC01("UC-01: Visualizar Gemelo Digital 3D del Edificio\n(Rotación 360°, Vista Expandida por Niveles 1-3)")
        UC02("UC-02: Monitorear Vibración RMS en Tiempo Real\n(10 Sensores vía WebSockets STOMP)")
        UC03("UC-03: Inyectar Falla Crítica (>90 mm/s) o\nRáfaga de Advertencia (3x >75 mm/s)")
        UC04("UC-04: Pausar / Reanudar Simulación Automática\nde Planta (Ciclo Realista de 12s)")
        UC05("UC-05: Evaluar Ventana Móvil Stateful y\nTransición de Estado (Patrones State + Strategy)")
    end

    SIM --> UC02
    SIM --> UC05
    OP --> UC01
    OP --> UC02
    OP --> UC03
    OP --> UC04
    UC03 -.->|"<<triggers>>"| UC05
```

---

## 2. Subsistema B: Toma de Decisiones, Enrutamiento Diferido y Actuación (RabbitMQ)

```mermaid
flowchart LR
    OP(["👷 Operador de Planta\n(ROLE_OPERATOR)"])
    ADM(["🛡️ Administrador / Ingeniero\n(ROLE_ADMIN)"])

    subgraph SUB_DECISIONES["Subsistema 2: Flujo de Decisiones Humanas y Workers Concurrentes"]
        direction TB
        UC06("UC-06: Recibir Alerta con Opciones Dinámicas\n(Fanout Exchange human_alerts)")
        UC07("UC-07: Autorizar Parada de Emergencia Inmediata\n(APAGADO_INMEDIATO -> critical_actions_queue)")
        UC08("UC-08: Programar Orden de Mantenimiento Inmediata\n(PROGRAMAR_MANTENIMIENTO_AHORA -> maintenance_queue)")
        UC09("UC-09: Programar Orden Diferida con Retardo\n(RECONOCER_Y_ESPERAR_24H -> RabbitMQ x-delay)")
        UC10("UC-10: Ignorar Temporalmente Alerta de Bajo Riesgo\n(IGNORAR_10_MINUTOS)")
        UC11("UC-11: Reactivar Máquina Detenida y Normalizar\nEstado en Detector, Actuador y Planta")
    end

    OP --> UC06
    OP --> UC07
    OP --> UC08
    OP --> UC09
    OP --> UC10
    OP --> UC11

    ADM --> UC07
    ADM --> UC08
    ADM --> UC09
    ADM --> UC11
```

---

## 3. Subsistema C: Auditoría Transaccional ACID, Seguridad OS y Servicios AWS Cloud

```mermaid
flowchart LR
    OP(["👷 Operador / Ingeniero\nAutenticado"])
    CRON(["⚙️ Demonios Systemd / Cron\n(Ubuntu Server 24.04 LTS)"])
    AWS(["☁️ Servicios Nativos AWS\n(SNS, S3, CloudWatch, ACM)"])

    subgraph SUB_AUDITORIA["Subsistema 3: Persistencia ACID, Notificación ISO 10816-3 y Resiliencia Cloud"]
        direction TB
        UC12("UC-12: Registrar Acta Inmutable en PostgreSQL\n(Tabla operator_decision_audit con Principal Auth)")
        UC13("UC-13: Filtrar y Exportar Histórico de Auditoría\nen Formato CSV Corporativo")
        UC14("UC-14: Despachar Boletín Técnico ISO 10816-3\nvía Amazon SNS (Correo Institucional & SMS)")
        UC15("UC-15: Ejecutar Backup Diario de PostgreSQL\ncon Firma SHA-256 y Réplica Cifrada en AWS S3")
        UC16("UC-16: Publicar Métricas de RAM, Swap, Disco\ny Contenedores hacia AWS CloudWatch")
        UC17("UC-17: Sincronizar y Desplegar Automáticamente\nNuevos Commits desde la Rama main de GitHub")
    end

    OP --> UC12
    OP --> UC13
    OP --> UC14

    CRON --> UC15
    CRON --> UC16
    CRON --> UC17

    UC14 --> AWS
    UC15 --> AWS
    UC16 --> AWS
```

---

## 4. Matriz de Trazabilidad de Casos de Uso vs. Microservicios

| Código | Caso de Uso | Microservicio / Componente Responsable | Tecnología Clave |
| :--- | :--- | :--- | :--- |
| **UC-01 / UC-02** | Gemelo Digital 3D y Telemetría en Vivo | `frontend_dashboard` + `plant_monitor_backend` | React 18, Canvas 3D, WebSocket STOMP |
| **UC-03 / UC-04** | Simulación Automática e Inyección de Fallas | `sensor_producer` | Apache Kafka (`sensor_data`, Key=`sensorId`) |
| **UC-05** | Detección Stateful en Ventana Móvil | `alert_detector` | Kafka Consumer + Patrones `State` y `Strategy` |
| **UC-06** | Generación de Opciones y Fanout | `alert_router` + `operator_console_backend` | RabbitMQ Fanout Exchange (`human_alerts`) |
| **UC-07** | Parada de Emergencia Inmediata | `action_dispatcher` $\rightarrow$ `actuator_worker` (x2) | RabbitMQ Direct (`critical_actions_queue`) |
| **UC-08 / UC-09** | Mantenimiento Inmediato y Diferido | `action_dispatcher` $\rightarrow$ `maintenance_worker` (x2) | Plugin `rabbitmq_delayed_message_exchange` (`x-delay`) |
| **UC-11** | Reactivación Integral de Máquina | `plant_monitor`, `alert_detector`, `actuator_worker` | Endpoints REST de Reset Coordinado |
| **UC-12 / UC-13** | Auditoría Transaccional Inmutable | `action_dispatcher` + `postgres` | PostgreSQL 16 ACID + Spring Security + `afterCommit` |
| **UC-14** | Boletín Corporativo ISO 10816-3 | `action_dispatcher`, `alert_router`, `scada-sns-bridge` | Amazon SNS (`us-east-2`) + Rol IAM `IMDSv2` |
| **UC-15 / UC-16 / UC-17** | Backups S3, CloudWatch y Auto-Deploy | Ubuntu Server 24.04 LTS (`hardening-ubuntu.sh`) | Amazon S3 (`AES256`), CloudWatch y Cron GitOps |
