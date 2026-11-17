# Plataforma de Mantenimiento Predictivo Interactivo (IoT Industrial)

Arquitectura empresarial híbrida orientada a eventos que integra **Apache Kafka**, **RabbitMQ** (con `rabbitmq_delayed_message_exchange`), **PostgreSQL 16**, **Java 17+ / Spring Boot 3.3**, **Frontend React 18 + TypeScript + Vite + TailwindCSS**, **Programación Orientada a Objetos Avanzada (Dominio Rico Puro, SOLID, Patrones State y Strategy)**, **Pruebas Unitarias Aisladas y de Contratos JSON (JUnit 5 y Mockito)** y **Docker Compose con Gobernanza de Recursos**.

---

## 1. Estructura Seccionada del Proyecto (`Frontend/` y `Backend/`)

El repositorio está organizado en dos grandes módulos funcionales más su infraestructura de contenedores y despliegue Cloud:

```text
TrabajoFinalPoo_SD_SO/
├── Frontend/                                # SPA React 18 + TypeScript + Vite + TailwindCSS (:3000)
│   ├── src/
│   │   ├── types/contracts.ts               # Contratos TypeScript alineados 100% con los records de Java
│   │   ├── hooks/useStompWebSocket.ts       # Hook STOMP 1.2 sobre WebSocket (:8081 / :8082) con reconexión
│   │   ├── services/api.ts                  # Cliente HTTP real con Basic Auth (sin simulaciones locales)
│   │   ├── App.tsx                          # Dashboard SCADA + Consola Interactiva + Auditoría PostgreSQL
│   │   └── main.tsx
│   ├── Dockerfile                           # Build multietapa (Node 20 Alpine -> Nginx 1.27 Alpine)
│   └── nginx.conf                           # Reverse Proxy REST y WebSocket (/ws-plant, /ws-alerts)
│
├── Backend/                                 # Los 8 Microservicios Java 17 + Spring Boot 3.3
│   ├── pom.xml                              # Agregador Maven para compilar y probar los 8 microservicios
│   ├── sensor_producer/                     # [1] Generador e inyector de telemetría hacia Kafka (:8089)
│   ├── alert_detector/                      # [2] Consumidor Stateful (Ventana Móvil + Strategy + State) (:8088)
│   ├── alert_router/                        # [3] Puente Híbrido Kafka -> RabbitMQ Fanout human_alerts (:8087)
│   ├── plant_monitor_backend/               # [4] Backend WebSocket /ws-plant en tiempo real (:8081)
│   ├── operator_console_backend/            # [5] Backend WebSocket /ws-alerts desde RabbitMQ (:8082)
│   ├── action_dispatcher/                   # [6] API REST (/decide) + Spring Security + PostgreSQL + x-delay (:8080)
│   ├── actuator_worker/                     # [7] Worker de parada física y reactivación (2 réplicas: 8091-8092)
│   └── maintenance_worker/                  # [8] Worker de órdenes inmediatas y diferidas (2 réplicas: 8093-8094)
│
├── docker/
│   ├── rabbitmq/Dockerfile                  # RabbitMQ 3.13 con plugin rabbitmq_delayed_message_exchange
│   └── postgres/init.sql                    # Esquema relacional transaccional operator_decision_audit
├── aws/
│   ├── deploy-aws.sh                        # Script de aprovisionamiento automatizado en AWS EC2
│   └── README_AWS_DEPLOYMENT.md             # Guía de despliegue en la nube y Security Groups
└── docker-compose.yml                       # Orquestación completa con límites de CPU/RAM y réplicas
```

### Arquitectura Interna Estandarizada por Microservicio (`Backend/<servicio>/`)

Cada uno de los 8 microservicios en `Backend/` implementa estrictamente la arquitectura limpia/hexagonal por capas:

```text
src/
├── main/
│   ├── java/com/empresa/mantenimiento/
│   │   ├── <Servicio>Application.java
│   │   ├── config/                          # SecurityConfig, CorsConfig, BeanConfig
│   │   ├── domain/                          # Núcleo 100% Java Puro (cero imports de Spring Framework)
│   │   │   ├── model/                       # Entidades de Dominio Rico, Records (@JsonValue) y Patrón State
│   │   │   ├── repository/                  # Interfaces de Puerto (DIP - Inversión de Dependencias)
│   │   │   ├── exception/                   # Excepciones de negocio Fail-Fast
│   │   │   └── service/                     # Servicios de dominio puros e implementaciones Strategy
│   │   ├── application/                     # Orquestación de casos de uso
│   │   │   ├── usecase/
│   │   │   ├── dto/
│   │   │   └── service/
│   │   ├── infrastructure/                  # Adaptadores hacia Kafka, RabbitMQ, PostgreSQL y WebSockets
│   │   │   ├── persistence/                 # Entidades JPA/Memoria, Repositorios y Mappers
│   │   │   └── adapter/
│   │   ├── presentation/                    # Controladores REST, DTOs con Bean Validation (@Valid) y Mappers
│   │   │   ├── controller/
│   │   │   ├── request/
│   │   │   ├── response/
│   │   │   └── mapper/
│   │   └── shared/                          # GlobalExceptionHandler, constantes, utilidades y ApiResponse
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       └── application-prod.yml
└── test/
    └── java/com/empresa/mantenimiento/      # Pruebas unitarias aisladas y de contratos JSON (JUnit 5 + Mockito, AAA)
        ├── domain/
        ├── application/
        ├── infrastructure/
        └── presentation/
```

---

## 2. Justificación Arquitectónica (Kafka vs. RabbitMQ vs. PostgreSQL)

1. **Flujo de Datos de Alta Frecuencia (Apache Kafka):**
   - Los 10 sensores industriales (`sensor_A` a `sensor_J`) emiten telemetría continua al tópico `sensor_data` (3 particiones) utilizando el `sensor_id` como **Partition Key**.
   - Esto garantiza **orden estricto de llegada por sensor** hacia la misma instancia de `alert_detector`, permitiendo mantener una **ventana móvil en memoria (`SensorVibrationWindow`)** exacta y libre de condiciones de carrera.
   - El **Patrón State (`NormalSensorState`, `WarningSensorState`, `CriticalSensorState`)** gobierna las transiciones de severidad y emite alertas hacia los tópicos `alerts_critical` y `alerts_warning` únicamente en transiciones o escalamientos de estado, previniendo tormentas de alertas.

2. **Flujo de Decisiones e Interacción Humana Diferida (RabbitMQ):**
   - Una vez detectada una anomalía en Kafka (`alerts_critical` o `alerts_warning`), `alert_router` determina polimórficamente las opciones de mitigación (`["APAGADO_INMEDIATO", "IGNORAR_10_MINUTOS"]` o `["PROGRAMAR_MANTENIMIENTO_AHORA", "RECONOCER_Y_ESPERAR_24H"]`) y publica la solicitud en el exchange `human_alerts` (`fanout`) de RabbitMQ.
   - Cuando el operador humano autenticado elige una acción en el dashboard React (`POST /decide` en `action_dispatcher`), el sistema **primero persiste transaccionalmente la auditoría en PostgreSQL** y, tras confirmar el commit (`TransactionSynchronization`), despacha el comando a RabbitMQ:
     - `APAGADO_INMEDIATO` &rarr; Cola directa `critical_actions_queue` consumida por las 2 réplicas de `actuator_worker`.
     - `PROGRAMAR_MANTENIMIENTO_AHORA` &rarr; Cola directa `maintenance_queue` consumida por las 2 réplicas de `maintenance_worker`.
     - `RECONOCER_Y_ESPERAR_24H` &rarr; Exchange `delayed_actions_exchange` (`x-delayed-message`) con cabecera `x-delay: 10000` (10 segundos simulando 24h), reteniendo el mensaje en el broker antes de liberarlo en `maintenance_queue`.

3. **Persistencia Relacional y Auditoría Transaccional (PostgreSQL):**
   - Cada resolución tomada por el operador humano se registra usando el principal autenticado (`Authentication`) en la tabla `operator_decision_audit` de PostgreSQL (`alert_id`, `sensor_id`, `chosen_action`, `operator_id`, `status`, `routing_target`, `delay_millis`, `notes`, `recorded_at`).

---

## 3. Ejecución de Pruebas Unitarias Aisladas (TDD)

Para ejecutar las suites de pruebas unitarias aisladas (dominio puro, aplicación, infraestructura, presentación y contratos de serialización JSON) en memoria RAM sin requerir contenedores activos:

```bash
mvn test -f Backend/pom.xml
```

---

## 4. Despliegue Completo con Docker Compose

```bash
docker compose up --build -d
```

- **Dashboard Web Interactivo (React 18 + TypeScript + Nginx):** `http://localhost:3000` (también accesible en `http://localhost:3000/dashboard.html`).
- **Action Dispatcher API (Spring Security Basic Auth `operator` / `operator123` o `admin` / `admin123`):** `POST http://localhost:8080/decide` y `GET http://localhost:8080/api/v1/audit`.
