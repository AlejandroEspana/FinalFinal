# Plataforma de Mantenimiento Predictivo Interactivo (IoT Industrial)

Arquitectura empresarial híbrida orientada a eventos que integra **Apache Kafka**, **RabbitMQ** (con `rabbitmq_delayed_message_exchange`), **PostgreSQL 16**, **Java 17+ / Spring Boot 3.3**, **Programación Orientada a Objetos Avanzada (Dominio Rico, SOLID, Patrones State y Strategy)**, **Pruebas Unitarias Aisladas (TDD con JUnit 5 y Mockito)** y **Docker Compose con Gobernanza de Recursos**.

---

## 1. Estructura Seccionada del Proyecto (`Frontend/` y `Backend/`)

El repositorio está organizado en dos grandes módulos funcionales más su infraestructura de contenedores y despliegue Cloud:

```text
TrabajoFinalPoo_SD_SO/
├── Frontend/                                # Aplicación Web Interactiva (SCADA + Consola de Decisiones)
│   ├── dashboard.html                       # Dashboard Dual WebSocket (:8081/:8082) + Inyector + Auditoría PG
│   ├── Dockerfile                           # Servidor Nginx ligero para el Frontend (:3000)
│   └── nginx.conf
│
├── Backend/                                 # Los 8 Microservicios Java 17 + Spring Boot 3.3
│   ├── pom.xml                              # Agregador Maven para compilar y probar los 8 microservicios
│   ├── sensor_producer/                     # [1] Generador e inyector de telemetría hacia Kafka (:8089)
│   ├── alert_detector/                      # [2] Consumidor Stateful (Ventana Móvil + Strategy + State) (:8088)
│   ├── alert_router/                        # [3] Puente Híbrido Kafka -> RabbitMQ Fanout human_alerts (:8087)
│   ├── plant_monitor_backend/               # [4] Backend WebSocket /ws-plant en tiempo real (:8081)
│   ├── operator_console_backend/            # [5] Backend WebSocket /ws-alerts desde RabbitMQ (:8082)
│   ├── action_dispatcher/                   # [6] API REST + Spring Security + PostgreSQL + x-delay (:8080)
│   ├── actuator_worker/                     # [7] Worker de parada física crítica (critical_actions_queue) (:8085)
│   └── maintenance_worker/                  # [8] Worker de órdenes inmediatas y diferidas (maintenance_queue) (:8086)
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

Cada uno de los 8 microservicios en `Backend/` implementa estrictamente la arquitectura limpia por capas y paquetes:

```text
src/
├── main/
│   ├── java/com/empresa/mantenimiento/
│   │   ├── <Servicio>Application.java
│   │   ├── config/                          # SecurityConfig, CorsConfig, SwaggerConfig, BeanConfig
│   │   ├── domain/                          # Núcleo puro (sin dependencias de infraestructura)
│   │   │   ├── model/                       # Entidades de Dominio Rico, Records inmutables y Patrón State
│   │   │   ├── repository/                  # Interfaces de Puerto (DIP - Inversión de Dependencias)
│   │   │   ├── exception/                   # Excepciones de negocio Fail-Fast
│   │   │   └── service/                     # Servicios de dominio y Patrón Strategy
│   │   ├── application/                     # Orquestación de casos de uso
│   │   │   ├── usecase/
│   │   │   ├── dto/
│   │   │   └── service/
│   │   ├── infrastructure/                  # Adaptadores hacia Kafka, RabbitMQ, PostgreSQL y WebSockets
│   │   │   ├── persistence/                 # Entidades JPA/Memoria, Repositorios y Mappers
│   │   │   ├── security/
│   │   │   ├── external/
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
│       ├── application-prod.yml
│       ├── db/migration/
│       └── static/
└── test/
    └── java/com/empresa/mantenimiento/      # Pruebas unitarias aisladas (JUnit 5 + Mockito, patrón AAA)
        ├── domain/
        ├── application/
        ├── infrastructure/
        └── presentation/
```

---

## 2. Justificación Arquitectónica (Kafka vs. RabbitMQ vs. PostgreSQL)

1. **Flujo de Datos de Alta Frecuencia (Apache Kafka):**
   - Los 10 sensores industriales (`sensor_A` a `sensor_J`) emiten telemetría continua al tópico `sensor_telemetry` (3 particiones) utilizando el `sensor_id` como **Partition Key**.
   - Esto garantiza **orden estricto de llegada por sensor** hacia la misma instancia de `alert_detector`, permitiendo mantener una **ventana móvil en memoria (`SensorVibrationWindow`)** exacta sin condiciones de carrera entre particiones.
   - Permite que múltiples grupos de consumidores independientes (`alert_detector_group` y `plant_monitor_group`) consuman el mismo flujo en tiempo real sin bloquearse mutuamente.

2. **Flujo de Decisiones e Interacción Humana Diferida (RabbitMQ):**
   - Una vez detectada una anomalía en Kafka (`alerts_stream`), `alert_router` la enriquece con las opciones de negocio según su severidad y la publica en el exchange `human_alerts` (`fanout`) de RabbitMQ.
   - Cuando el operador humano elige una acción en el dashboard (`POST /api/v1/decide` en `action_dispatcher`), RabbitMQ permite enrutamiento selectivo y temporizado:
     - `APAGADO_INMEDIATO` &rarr; Cola directa `critical_actions_queue` consumida por las réplicas de `actuator_worker`.
     - `PROGRAMAR_MANTENIMIENTO_AHORA` &rarr; Cola directa `maintenance_queue` consumida por las réplicas de `maintenance_worker`.
     - `RECONOCER_Y_ESPERAR_24H` &rarr; Exchange `delayed_actions_exchange` (`x-delayed-message`) con cabecera `x-delay: 10000` (10 segundos simulando 24h), reteniendo el mensaje en el broker antes de liberarlo en `maintenance_queue`.

3. **Persistencia Relacional y Auditoría Transaccional (PostgreSQL):**
   - Cada resolución tomada por el operador humano se registra transaccionalmente (`@Transactional`) en la tabla `operator_decision_audit` de PostgreSQL mediante Spring Data JPA / Hibernate, garantizando trazabilidad inmutable (`alert_id`, `sensor_id`, `chosen_action`, `operator_id`, `routing_target`, `delay_ms`, `notes`, `created_at`).

---

## 3. Ejecución de Pruebas Unitarias Aisladas (TDD)

Para ejecutar las 32 suites de pruebas unitarias aisladas (dominio, aplicación, infraestructura y presentación) en memoria RAM sin requerir contenedores activos:

```bash
cd Backend
mvn clean test
```

---

## 4. Despliegue Completo con Docker Compose

```bash
docker compose up --build -d
```

- **Dashboard Web Interactivo (Frontend SCADA + Consola):** `http://localhost:3000` (o abriendo `Frontend/dashboard.html` directamente en el navegador).
- **RabbitMQ Management UI:** `http://localhost:15672` (`guest` / `guest`).
- **Action Dispatcher API (Spring Security Basic Auth `operator` / `operator123`):** `http://localhost:8080/api/v1/audit` y `POST http://localhost:8080/api/v1/decide`.

# FinalFinal
