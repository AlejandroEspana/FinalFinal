# Guion de Exposición y Sustentación — Plataforma de Mantenimiento Predictivo Interactivo (IoT Industrial)

**Asignaturas Integradas**: Programación Orientada a Objetos Avanzada (POO) • Sistemas Distribuidos (SD) • Sistemas Operativos (SO)
**URL Pública en Producción (AWS CloudFront + TLS Nativo)**: `https://dnzhr4v1oxvn7.cloudfront.net`

---

## Parte 1: Introducción y Resumen Ejecutivo (¿Qué construimos?)

> *"Buenos días/tardes. Presentamos nuestra **Plataforma Distribuida de Mantenimiento Predictivo Interactivo para IoT Industrial con Gemelo Digital 3D**, desplegada en la nube de **Amazon Web Services (AWS)** sobre **Ubuntu Server 24.04 LTS**.*
>
> *El sistema resuelve un problema crítico en la industria: monitorear en tiempo real un flujo continuo de vibraciones mecánicas bajo la **norma ISO 10816-3**, detectar patrones de falla mediante procesamiento **stateful** sin generar tormentas de alertas, presentar opciones dinámicas de intervención a un operador humano en una consola interactiva con **Gemelo Digital 3D del edificio**, auditar cada decisión humana con garantías **ACID en PostgreSQL**, enrutar comandos inmediatos o diferidos hacia workers concurrentes y despachar boletines técnicos oficiales al correo institucional mediante **Amazon SNS**."*

---

## Parte 2: Justificación de la Arquitectura Híbrida (¿Por qué Kafka + RabbitMQ + PostgreSQL?)

El caso de estudio nos retó a separar el **Flujo de Datos** del **Flujo de Decisiones** y la **Persistencia de Auditoría**. Así se implementó y justificó cada uno:

1. **Apache Kafka (El Bus de Eventos de Alto Consumo y Flujo de Datos)**:
   * **¿Dónde se usa?**: En la ingesta de lecturas (`sensor_data` con 3 particiones) y la emisión de anomalías (`alerts_warning` y `alerts_critical`).
   * **¿Por qué Kafka aquí?**: Porque soporta alto throughput de telemetría como un log distribuido inmutable y permite **particionamiento por clave (`Key = sensorId`)**. Al usar el ID del sensor (`sensor_A` ... `sensor_J`) como clave de partición, Kafka garantiza matemáticamente que todas las lecturas de una misma máquina lleguen **en estricto orden cronológico** a la misma instancia consumidora para evaluar la ventana móvil sin condiciones de carrera.
2. **RabbitMQ (El Bus de Enrutamiento Complejo y Flujo de Decisiones)**:
   * **¿Dónde se usa?**: Desde que una alerta requiere intervención humana hasta que el comando llega a los actuadores o cuadrillas de mantenimiento.
   * **¿Por qué RabbitMQ aquí?**: Porque necesitamos patrones avanzados de enrutamiento AMQP que Kafka no ofrece de forma nativa:
     * **Fanout Exchange (`human_alerts`)**: Para difundir alertas hacia la consola del operador.
     * **Direct Exchange (`immediate_actions_exchange`)**: Para enrutar selectivamente según la decisión humana (`critical.shutdown` hacia `critical_actions_queue` o `maintenance.immediate` hacia `maintenance_queue`).
     * **Delayed Message Exchange (`delayed_actions_exchange` con plugin `rabbitmq_delayed_message_exchange`)**: Para gestionar tareas diferidas con la cabecera **`x-delay`** cuando el operador elige `RECONOCER_Y_ESPERAR_24H`.
3. **PostgreSQL 16 (El Motor Relacional Transaccional ACID de Auditoría)**:
   * **¿Dónde se usa?**: En `action_dispatcher` (tabla `operator_decision_audit`).
   * **¿Por qué PostgreSQL aquí?**: En un entorno industrial, ninguna orden de parada de planta o mantenimiento puede ejecutarse sin dejar un rastro forense inmutable de quién la autorizó, cuándo y por qué. Implementamos sincronización transaccional **`TransactionSynchronization.afterCommit()`** para garantizar que el comando solo salga hacia RabbitMQ y Amazon SNS después de que el `COMMIT` en PostgreSQL haya sido exitoso.

---

## Parte 3: ¿Para qué sirve cada uno de los 8 Microservicios Desplegados?

Todos los microservicios están desarrollados en **Java 17 y Spring Boot 3.3.5**, siguiendo una **Arquitectura Hexagonal de 4 capas** (`domain`, `application`, `infrastructure`, `presentation`):

| # | Microservicio | Función Principal en la Plataforma | Entradas y Salidas |
| :-: | :--- | :--- | :--- |
| **1** | **`sensor_producer`** | **Simulador Industrial e Inyector de Telemetría**: Genera lecturas realistas cada 12 segundos para los 10 sensores del edificio (`sensor_A` a `sensor_J`) y expone endpoints REST para pausar/reanudar la simulación o inyectar picos críticos (`>90 mm/s`) y ráfagas (`3x >75 mm/s`). | **Salida**: Tópico Kafka `sensor_data` (particionado por `Key = sensorId`). |
| **2** | **`alert_detector`** | **Motor Analítico Stateful en Ventana Móvil**: Mantiene una ventana deslizante (`SensorStreamWindow`) en memoria por sensor. Aplica el **Patrón Strategy** para detectar anomalías y el **Patrón State** para emitir alertas únicamente en la **transición de estado** (`Normal` $\rightarrow$ `Warning` o `Critical`), evitando tormentas de alertas. | **Entrada**: Kafka `sensor_data`.<br>**Salida**: Kafka `alerts_warning` y `alerts_critical`. |
| **3** | **`alert_router`** | **Enrutador y Generador de Opciones**: Consume las alertas detectadas en Kafka, invoca el servicio de dominio puro `AlertOptionGeneratorDomainService` para construir las opciones de acción humana y las publica hacia RabbitMQ y Amazon SNS. | **Entrada**: Kafka `alerts_*`.<br>**Salida**: RabbitMQ Fanout `human_alerts` + Amazon SNS. |
| **4** | **`plant_monitor_backend`** | **Monitor de Estado de Planta y Gemelo Digital 3D**: Consume el flujo de Kafka en tiempo real, mantiene el estado operativo de las 10 máquinas de los 3 pisos del edificio y transmite actualizaciones instantáneas por WebSocket STOMP al frontend. | **Entrada**: Kafka `sensor_data`.<br>**Salida**: WebSocket `/ws-plant` y API `/api/plant/status`. |
| **5** | **`operator_console_backend`** | **Consola de Alertas Pendientes del Operador**: Escucha la cola `human_alerts_console_queue` en RabbitMQ, almacena las alertas activas pendientes de decisión humana y las empuja en vivo por WebSocket hacia el navegador. | **Entrada**: RabbitMQ `human_alerts_console_queue`.<br>**Salida**: WebSocket `/ws-alerts` y `DELETE /api/alerts/{id}`. |
| **6** | **`action_dispatcher`** | **Cerebro Transaccional, Seguridad y Auditoría**: Protegido con **Spring Security (`ROLE_OPERATOR` y `ROLE_ADMIN`)**. Recibe la decisión del operador, toma su identidad real del `Principal` autenticado, guarda el acta en **PostgreSQL**, y tras el `COMMIT` enruta el comando a RabbitMQ (inmediato o diferido `x-delay`) y envía el boletín ISO 10816-3 por **Amazon SNS**. | **Entrada**: `POST /api/dispatcher/decide`.<br>**Salida**: PostgreSQL `operator_decision_audit`, RabbitMQ Exchanges y Amazon SNS. |
| **7** | **`actuator_worker` (2 Réplicas)** | **Ejecutor de Paradas de Emergencia**: Desplegado con **2 réplicas concurrentes** en Docker Compose. Consume en balanceo *round-robin* la cola `critical_actions_queue` cuando el operador ordena `APAGADO_INMEDIATO`, cortando la energía de la máquina (`EMERGENCY_HALTED`). | **Entrada**: RabbitMQ `critical_actions_queue`.<br>**Salida**: Estado del actuador y reactivación (`reset`). |
| **8** | **`maintenance_worker` (2 Réplicas)** | ** Gestor de Órdenes de Mantenimiento Inmediatas y Diferidas**: Desplegado con **2 réplicas concurrentes**. Consume la cola `maintenance_queue` tanto para órdenes inmediatas (`PROGRAMAR_MANTENIMIENTO_AHORA`) como para órdenes que maduran tras el tiempo de espera en el plugin `x-delayed-message` (`RECONOCER_Y_ESPERAR_24H`). | **Entrada**: RabbitMQ `maintenance_queue` (directo o vía `delayed_actions_exchange`). |

---

## Parte 4: ¿Dónde se Aplicaron los Principios de POO Avanzada y Calidad de Software?

1. **Dominio Rico y Puro (Sin Anotaciones de Framework)**:
   * Ninguna clase dentro de `domain/model` o `domain/service` importa paquetes de Spring (`@Service`, `@Component`) ni de JPA. Los servicios de dominio se registran mediante clases `@Configuration` en la capa de infraestructura.
2. **Value Objects Inmutables con Java `record`**:
   * `SensorId`, `AlertId` y `VibrationReadingRecord` validan sus invariantes en el constructor compacto (rechazando nulos, vacíos o vibraciones negativas) y usan `@JsonValue` / `@JsonCreator` para serializar contratos JSON limpios.
3. **Patrón de Diseño State (`SensorOperationalState`)**:
   * Implementado en `NormalSensorState`, `WarningSensorState` y `CriticalSensorState` dentro de `alert_detector`. Resuelve el problema de las tormentas de alertas emitiendo eventos solo cuando el sensor cambia de estado.
4. **Patrón de Diseño Strategy (`AnomalyDetectionStrategy`)**:
   * Implementado en `ThresholdSlidingWindowStrategy` (umbrales fijos 75/90 mm/s) y `ExponentialMovingAverageStrategy` (suavizado exponencial EMA), inyectables por configuración sin modificar el caso de uso (`Open/Closed Principle`).
5. **Pruebas Unitarias Aisladas con Patrón AAA (Arrange-Act-Assert)**:
   * **49 pruebas automatizadas** distribuidas en los 8 microservicios, incluyendo pruebas de lógica de dominio, máquina de estados, estrategias de detección, seguridad HTTP Basic y contratos de serialización JSON.

---

## Parte 5: Sistemas Operativos (Hardening Ubuntu Server) y Despliegue Cloud en AWS

Además de los requisitos base, llevamos el proyecto a un nivel de producción real aplicando **12 fases de Ingeniería de Sistemas Operativos y Cloud en AWS (`aws/hardening-ubuntu.sh`)**:

1. **Memoria Virtual Swap de 6 GB en SSD y Tuning de Kernel**: Permite ejecutar con holgura los 15 contenedores sobre la capa gratuita de AWS EC2.
2. **Aislamiento Total de Puertos en `127.0.0.1` y Reverse Proxy Nginx**: Ningún puerto de base de datos (`5432`), brokers (`9092`, `5672`, `15672`) ni microservicios (`8080-8094`) está expuesto a Internet. Todo entra por Nginx con resolución DNS dinámica (`127.0.0.11`).
3. **Dominio Limpio sin IPs ni Puertos vía Amazon CloudFront (`https://dnzhr4v1oxvn7.cloudfront.net`)**: Distribución CDN oficial de AWS con certificado TLS/SSL nativo de **Amazon Trust Services** y registro en **AWS Certificate Manager (ACM)**.
4. **Hardening de SSH, PAM, Sudo, UFW, Fail2ban, Kernel `sysctl` (ASLR) y `auditd`**: Protección activa contra fuerza bruta, bloqueo de IPs atacantes en tiempo real y auditoría forense de privilegios evaluada con **Lynis**.
5. **Notificaciones Reales vía Amazon SNS (Norma ISO 10816-3)**: Servicio `systemd` confinado (`scada-sns-bridge.service`) autenticado por **Rol IAM (`IMDSv2`)** sin guardar llaves de AWS en disco, enviando actas formales al correo institucional.
6. **Backups Diarios de PostgreSQL con Firma Criptográfica `SHA-256` y Réplica Cifrada `AES-256` en Amazon S3**: Automatizados en `/usr/local/sbin/backup-postgres-scada.sh` hacia el bucket privado `s3://scada-ucc-backups-148395526595`.
7. **Monitoreo de RAM, Swap, Disco y Contenedores en AWS CloudWatch** + **Auto-Deploy Continuo GitOps** sincronizado cada 2 minutos con la rama `main` de GitHub.
