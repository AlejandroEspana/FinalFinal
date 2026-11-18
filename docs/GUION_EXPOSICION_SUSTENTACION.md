# Guion Maestro de Exposición y Sustentación Técnica — Plataforma SCADA IoT Industrial

**Proyecto**: Plataforma de Mantenimiento Predictivo Interactivo con Gemelo Digital 3D
**Asignaturas**: Programación Orientada a Objetos Avanzada (POO) • Sistemas Distribuidos (SD) • Sistemas Operativos (SO)
**URL Oficial en Producción (AWS CloudFront + TLS Nativo)**: `https://dnzhr4v1oxvn7.cloudfront.net`

---

## 1. ¿Cuál es el "Microservicio Padre" o el que Controla y Recibe Más Información?

Si el profesor pregunta: ***"¿Cuál es el microservicio padre o el que controla todo el sistema?"***, esta es la respuesta arquitectónica exacta:

> *"Al ser una **arquitectura de microservicios orientada a eventos (Event-Driven Architecture)**, no existe un monolito único que bloquee a los demás; sin embargo, el sistema tiene **dos cerebros o núcleos centrales** según el flujo:*
>
> 1. **En el Flujo de Datos Masivos (El que recibe más volumen de información): `alert_detector` y `plant_monitor_backend`**
>    * Ambos reciben el **100% del flujo continuo de telemetría** desde el tópico `sensor_data` de **Apache Kafka** (las lecturas de los 10 sensores de toda la planta).
>    * **`alert_detector`** es el **Cerebro Analítico Stateful**: concentra toda la historia reciente en ventanas móviles (`SensorVibrationWindow`) y decide cuándo nace una alerta.
>
> 2. **En el Flujo de Decisiones, Seguridad y Persistencia (El "Microservicio Padre / Orquestador Transaccional"): `action_dispatcher`**
>    * **`action_dispatcher` es el microservicio de gobierno y control central de la plataforma**. ¿Por qué? Porque es el único que conecta **4 mundos al mismo tiempo**:
>      1. Valida la identidad y el rol del operador humano con **Spring Security (`ROLE_OPERATOR` / `ROLE_ADMIN`)**.
>      2. Es el dueño exclusivo de la base de datos transaccional **PostgreSQL 16 (`operator_decision_audit`)**.
>      3. Controla y ordena a **RabbitMQ** qué comando enviar a los **Workers de Actuación** (corte de energía inmediato) o a los **Workers de Mantenimiento** (orden inmediata o diferida con `x-delay`).
>      4. Dispara los boletines corporativos **ISO 10816-3** hacia **Amazon SNS** (Correo Institucional y SMS)."

---

## 2. Mapa Exacto de Archivos donde se Implementa Apache Kafka (Dónde está y Qué hace)

Apache Kafka maneja todo el **"Flujo de Datos" (alta ingesta y procesamiento stateful)**. Se toca exactamente en **4 microservicios** a través de los siguientes archivos:

### 2.1. En `sensor_producer` (Productor de Telemetría con Particionamiento por Clave)
* **Puerto de Dominio**: [TelemetryEventPublisherPort.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/sensor_producer/src/main/java/com/empresa/mantenimiento/domain/repository/TelemetryEventPublisherPort.java)
* **Adaptador Kafka**: [KafkaTelemetryPublisherAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/sensor_producer/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/KafkaTelemetryPublisherAdapter.java#L20-L58)
  * **¿Qué hace exactamente este archivo?**:
    * Recibe el Value Object inmutable `VibrationReadingRecord` (generado cada 12s por [GenerateTelemetryBatchUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/sensor_producer/src/main/java/com/empresa/mantenimiento/application/usecase/GenerateTelemetryBatchUseCase.java) o inyectado manualmente por [InjectCustomReadingUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/sensor_producer/src/main/java/com/empresa/mantenimiento/application/usecase/InjectCustomReadingUseCase.java)).
    * En la **línea 41**, extrae `String partitionKey = reading.sensorId().value();` (por ejemplo, `"sensor_A"`).
    * En la **línea 45**, ejecuta `kafkaTemplate.send("sensor_data", partitionKey, jsonPayload)`.
    * **¿Por qué es clave para la nota?**: Al pasar `partitionKey` como segundo parámetro de `kafkaTemplate.send(...)`, el algoritmo de hash de Kafka envía todas las lecturas de `"sensor_A"` siempre a la misma partición (de las 3 disponibles en `docker-compose.yml`), garantizando **orden cronológico estricto por sensor**.

### 2.2. En `alert_detector` (Consumidor Stateful de Telemetría y Productor de Alertas)
Aquí Kafka se usa tanto de **entrada** como de **salida**:
1. **Adaptador Kafka de Entrada (Consumidor)**: [KafkaSensorTelemetryConsumerAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/KafkaSensorTelemetryConsumerAdapter.java#L15-L42)
   * Usa la anotación `@KafkaListener(topics = "sensor_data", groupId = "alert-detector-group")`.
   * Deserializa el JSON a `VibrationReadingRecord` y llama al caso de uso [EvaluateSensorTelemetryUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/application/usecase/EvaluateSensorTelemetryUseCase.java#L35-L46), el cual busca la ventana móvil del sensor en [WindowRegistryDomainService.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/service/WindowRegistryDomainService.java) y ejecuta `window.procesarTelemetria(reading, detectionStrategy)` en [SensorVibrationWindow.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/SensorVibrationWindow.java#L66-L84).
2. **Adaptador Kafka de Salida (Productor de Alertas)**: [KafkaAlertPublisherAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/KafkaAlertPublisherAdapter.java#L20-L61)
   * Cuando `SensorVibrationWindow` detecta una **transición de estado**, invoca `publishAlert(IndustrialAlert alert)`.
   * En la **línea 44**, selecciona dinámicamente el tópico de destino según la severidad:
     `String targetTopic = (alert.severity() == AlertSeverity.CRITICAL) ? "alerts_critical" : "alerts_warning";`
   * Publica la alerta en Kafka manteniendo el `sensorId` como clave.

### 2.3. En `alert_router` (Puente Consumidor de Kafka hacia RabbitMQ)
* **Adaptador Kafka**: [KafkaAlertBridgeConsumerAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_router/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/KafkaAlertBridgeConsumerAdapter.java)
  * **¿Qué hace?**: Escucha simultáneamente los dos tópicos de alertas de Kafka (`@KafkaListener(topics = {"alerts_critical", "alerts_warning"}, groupId = "alert-router-group")`), convierte el JSON en `IndustrialAlert` y lo pasa a [RouteAlertToHumanUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_router/src/main/java/com/empresa/mantenimiento/application/usecase/RouteAlertToHumanUseCase.java) para que cruce hacia el mundo de RabbitMQ.

### 2.4. En `plant_monitor_backend` (Consumidor de Kafka en Tiempo Real para el Gemelo 3D)
* **Adaptador Kafka**: [KafkaPlantConsumerAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/plant_monitor_backend/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/KafkaPlantConsumerAdapter.java)
  * **¿Qué hace?**: Escucha el tópico `sensor_data` con su propio grupo consumidor independiente (`plant-monitor-group`), actualiza la vista materializada en [PlantMaterializedDomainService.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/plant_monitor_backend/src/main/java/com/empresa/mantenimiento/domain/service/PlantMaterializedDomainService.java) y empuja el cambio por WebSocket STOMP en [WebSocketPlantBroadcasterAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/plant_monitor_backend/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/WebSocketPlantBroadcasterAdapter.java) para mover en vivo las barras y el **Gemelo Digital 3D del Edificio**.

---

## 3. Mapa Exacto de Archivos donde se Implementa RabbitMQ (Dónde está y Qué hace)

RabbitMQ maneja todo el **"Flujo de Decisiones Humanas, Enrutamiento Selectivo y Tareas Diferidas (`x-delay`)"**. Se toca en **5 microservicios** a través de los siguientes archivos:

### 3.1. En `alert_router` (Publicación en Fanout Exchange `human_alerts`)
* **Configuración de Topología AMQP**: [BeanConfig.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_router/src/main/java/com/empresa/mantenimiento/config/BeanConfig.java) (declara el `FanoutExchange("human_alerts")`).
* **Servicio de Dominio que genera opciones**: [DecisionOptionsPolicyDomainService.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_router/src/main/java/com/empresa/mantenimiento/domain/service/DecisionOptionsPolicyDomainService.java) (asigna las opciones válidas `APAGADO_INMEDIATO`, `PROGRAMAR_MANTENIMIENTO_AHORA`, `RECONOCER_Y_ESPERAR_24H`, `IGNORAR_10_MINUTOS`).
* **Adaptador RabbitMQ de Salida**: [RabbitMQHumanAlertPublisherAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_router/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/RabbitMQHumanAlertPublisherAdapter.java#L70-L89)
  * **¿Qué hace?**: Serializa el objeto `HumanActionRequest` y en la **línea 75** ejecuta `rabbitTemplate.convertAndSend("human_alerts", "", payload)` (en Fanout la routing key va vacía `""` porque difunde a todas las colas suscritas). Además, si es `CRITICAL`, envía en asíncrono el boletín temprano a **Amazon SNS**.

### 3.2. En `operator_console_backend` (Consumo de la Cola del Fanout Exchange)
* **Adaptador RabbitMQ de Entrada**: [RabbitMQHumanAlertConsumerAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/operator_console_backend/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/RabbitMQHumanAlertConsumerAdapter.java)
  * **¿Qué hace?**: Con `@RabbitListener(queues = "human_alerts_console_queue")`, recibe la alerta con sus opciones desde RabbitMQ, la guarda en [PendingAlertsRegistryDomainService.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/operator_console_backend/src/main/java/com/empresa/mantenimiento/domain/service/PendingAlertsRegistryDomainService.java) y la transmite por WebSocket en [WebSocketOperatorAlertBroadcasterAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/operator_console_backend/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/WebSocketOperatorAlertBroadcasterAdapter.java) para que aparezca al instante en la consola derecha del operador.

### 3.3. En `action_dispatcher` (Enrutamiento Inmediato y Diferido con Plugin `x-delayed-message`)
* **Configuración del Exchange Diferido (`x-delayed-message`)**: [BeanConfig.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/config/BeanConfig.java)
  * Declara el `CustomExchange("delayed_actions_exchange", "x-delayed-message")` con el argumento `"x-delayed-type": "direct"` y su `Binding` hacia la cola `maintenance_queue` con routing key `maintenance_route`.
* **Caso de Uso Transaccional (`afterCommit`)**: [ProcessOperatorDecisionUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/application/usecase/ProcessOperatorDecisionUseCase.java#L44-L117)
  * Primero guarda la decisión en PostgreSQL (`auditRepositoryPort.recordAudit(decision)` en la línea 52) a través de [JpaDecisionAuditRepositoryAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/JpaDecisionAuditRepositoryAdapter.java).
  * En las **líneas 56-63**, registra `TransactionSynchronizationManager.registerSynchronization(...)` para que `dispatchToBroker(...)` se ejecute **únicamente dentro de `afterCommit()`**.
* **Adaptador RabbitMQ de Despacho**: [RabbitMQActionCommandPublisherAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/RabbitMQActionCommandPublisherAdapter.java#L52-L98)
  * Tiene los **3 métodos clave de despacho**:
    1. `dispatchShutdown(ShutdownCommand)` (**línea 53**): Envía orden de corte inmediato a la cola `critical_actions_queue`.
    2. `dispatchImmediateMaintenance(MaintenanceWorkOrder)` (**línea 72**): Envía orden inmediata a la cola `maintenance_queue`.
    3. `dispatchDelayedMaintenance(MaintenanceWorkOrder, long delayMillis)` (**línea 84**): Crea un `MessageProperties`, inyecta la cabecera **`props.setHeader("x-delay", delayMillis)`** (**línea 89**) y publica el mensaje en `delayed_actions_exchange`. El plugin `rabbitmq_delayed_message_exchange` retiene el mensaje durante los `10000 ms` (que simulan 24 horas) y luego lo deposita en `maintenance_queue`.

### 3.4. En `actuator_worker` (2 Réplicas Consumiendo Paradas Críticas)
* **Adaptador RabbitMQ**: [RabbitMQCriticalActionConsumerAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/actuator_worker/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/RabbitMQCriticalActionConsumerAdapter.java)
  * **¿Qué hace?**: Escucha `@RabbitListener(queues = "critical_actions_queue")`. Como hay **2 réplicas** levantadas en Docker Compose, RabbitMQ reparte los mensajes en **Round-Robin**. Ejecuta [ExecuteEmergencyShutdownUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/actuator_worker/src/main/java/com/empresa/mantenimiento/application/usecase/ExecuteEmergencyShutdownUseCase.java), pasando el actuador físico ([PhysicalActuator.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/actuator_worker/src/main/java/com/empresa/mantenimiento/domain/model/PhysicalActuator.java)) de `RunningMachineState` a `EmergencyHaltedMachineState`.

### 3.5. En `maintenance_worker` (2 Réplicas Consumiendo Mantenimiento Inmediato y Diferido)
* **Adaptador RabbitMQ**: [RabbitMQMaintenanceConsumerAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/maintenance_worker/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/RabbitMQMaintenanceConsumerAdapter.java)
  * **¿Qué hace?**: Escucha `@RabbitListener(queues = "maintenance_queue")`. Recibe tanto los tickets inmediatos como los tickets que acaban de cumplir su tiempo de retardo `x-delay` en el exchange diferido, y los procesa en [ProcessMaintenanceWorkOrderUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/maintenance_worker/src/main/java/com/empresa/mantenimiento/application/usecase/ProcessMaintenanceWorkOrderUseCase.java).

---

## 4. ¿En qué Archivos se Trabajan los 5 Principios SOLID y Cómo se Aplican?

Si en la sustentación de **POO Avanzada** te preguntan dónde están los principios **SOLID**, puedes mostrar estos archivos exactos:

### 4.1. `S` — Single Responsibility Principle (Principio de Responsabilidad Única)
* **¿Cómo se aplica?**: Cada clase tiene una única razón para cambiar. Separamos estrictamente la **regla de negocio**, la **coordinación de casos de uso**, la **persistencia SQL**, la **mensajería AMQP/Kafka** y la **presentación HTTP**.
* **Archivos de ejemplo en `action_dispatcher`**:
  * [MaintenanceDecision.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/domain/model/MaintenanceDecision.java): Solo valida las reglas de negocio de una decisión (qué cola le corresponde y cuánto retardo lleva).
  * [ProcessOperatorDecisionUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/application/usecase/ProcessOperatorDecisionUseCase.java): Solo coordina la transacción entre el puerto de base de datos y el puerto de mensajería.
  * [JpaDecisionAuditRepositoryAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/JpaDecisionAuditRepositoryAdapter.java): Solo sabe guardar en PostgreSQL mediante Spring Data JPA.
  * [RabbitMQActionCommandPublisherAdapter.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/RabbitMQActionCommandPublisherAdapter.java): Solo sabe construir tramas JSON y enviarlas a RabbitMQ.
  * [MultichannelNotificationService.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/infrastructure/adapter/MultichannelNotificationService.java): Solo sabe redactar y despachar el boletín corporativo ISO 10816-3 hacia Amazon SNS.

### 4.2. `O` — Open/Closed Principle (Principio Abierto/Cerrado)
* **¿Cómo se aplica?**: El motor de detección de anomalías está **abierto a la extensión** (podemos agregar nuevos algoritmos matemáticos de detección) pero **cerrado a la modificación** (no hay que tocar ni una línea de `SensorVibrationWindow` ni de `EvaluateSensorTelemetryUseCase`).
* **Archivos exactos en `alert_detector`**:
  * Interfaz [AnomalyDetectionStrategy.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/strategy/AnomalyDetectionStrategy.java)
  * Implementación 1 (Umbrales fijos ISO 10816-3: `>75 mm/s x3` o `>90 mm/s`): [RuleBasedThresholdStrategy.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/strategy/RuleBasedThresholdStrategy.java)
  * Implementación 2 (Media Móvil Exponencial EMA): [ExponentialMovingAverageStrategy.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/strategy/ExponentialMovingAverageStrategy.java)
  * Selección limpia en configuración: [BeanConfig.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/config/BeanConfig.java)

### 4.3. `L` — Liskov Substitution Principle (Principio de Sustitución de Liskov)
* **¿Cómo se aplica?**: Cualquier implementación de estado o estrategia puede sustituir a su interfaz base sin romper las invariantes ni lanzar excepciones inesperadas.
* **Archivos exactos en `alert_detector` y `actuator_worker`**:
  * En `alert_detector`: La interfaz [SensorRiskState.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/state/SensorRiskState.java) es implementada por [NormalSensorState.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/state/NormalSensorState.java), [WarningSensorState.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/state/WarningSensorState.java) y [CriticalSensorState.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/state/CriticalSensorState.java). `SensorVibrationWindow` invoca polimórficamente `currentState.shouldEmitAlert(result)` y `currentState.transition(result)` sin usar `instanceof` ni hacer *castings*.
  * En `actuator_worker`: La interfaz [MachineOperationalState.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/actuator_worker/src/main/java/com/empresa/mantenimiento/domain/model/MachineOperationalState.java) es sustituida limpiamente entre [RunningMachineState.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/actuator_worker/src/main/java/com/empresa/mantenimiento/domain/model/RunningMachineState.java) y [EmergencyHaltedMachineState.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/actuator_worker/src/main/java/com/empresa/mantenimiento/domain/model/EmergencyHaltedMachineState.java).

### 4.4. `I` — Interface Segregation Principle (Principio de Segregación de Interfaces)
* **¿Cómo se aplica?**: En vez de tener una interfaz gigante "Omnipotente" en el dominio, definimos **Puertos de Salida (`Port`) pequeños, granulares y específicos** para cada necesidad.
* **Archivos exactos**:
  * En `action_dispatcher`: Separamos el puerto de auditoría en base de datos ([DecisionAuditRepositoryPort.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/domain/repository/DecisionAuditRepositoryPort.java), que solo tiene `recordAudit` y `findAllRecent`) del puerto de publicación de comandos ([ActionCommandPublisherPort.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/domain/repository/ActionCommandPublisherPort.java), que solo tiene métodos de envío a colas). Así, el caso de uso de consulta de auditoría ([GetDecisionAuditHistoryUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/application/usecase/GetDecisionAuditHistoryUseCase.java)) solo depende de `DecisionAuditRepositoryPort` y no está forzado a conocer RabbitMQ.

### 4.5. `D` — Dependency Inversion Principle (Principio de Inversión de Dependencias)
* **¿Cómo se aplica?**: Los módulos de alto nivel (`domain` y `application`) **nunca importan ni dependen de módulos de bajo nivel (`infrastructure`)**. Ambos dependen de abstracciones (interfaces `*Port` ubicadas en el paquete `domain/repository`). Además, todas las clases usan **Inyección Estricta por Constructor** validada con `Objects.requireNonNull(...)` (cero uso de `@Autowired` en campos privados).
* **Archivos exactos donde se demuestra**:
  * [EvaluateSensorTelemetryUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/application/usecase/EvaluateSensorTelemetryUseCase.java#L25-L33): Recibe por constructor las interfaces `AnomalyDetectionStrategy` y `AlertPublisherPort`. No sabe que por debajo existe Kafka.
  * [RouteAlertToHumanUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_router/src/main/java/com/empresa/mantenimiento/application/usecase/RouteAlertToHumanUseCase.java): Recibe por constructor `HumanAlertPublisherPort`. No sabe que por debajo existe RabbitMQ ni Amazon SNS.
  * [ProcessOperatorDecisionUseCase.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/application/usecase/ProcessOperatorDecisionUseCase.java#L34-L42): Recibe por constructor `DecisionAuditRepositoryPort` y `ActionCommandPublisherPort`. Gracias a esta inversión de dependencias, en los **49 tests unitarios** podemos inyectar *Mocks* de Mockito en milisegundos sin levantar bases de datos ni brokers reales.

---

## 5. ¿Qué Hicimos en el `docker-compose.yml` y en los `Dockerfile`? (Orquestación de los 15 Contenedores)

Archivo principal: [docker-compose.yml](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker-compose.yml)

El archivo `docker-compose.yml` orquesta **15 contenedores en total** divididos en **3 bloques arquitectónicos**, aplicando buenas prácticas de seguridad, resiliencia y optimización para la Capa Gratuita de AWS:

### 5.1. Bloque 1: Infraestructura de Datos y Mensajería ([Líneas 5 a 101](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker-compose.yml#L5-L101))
1. **`postgres` (`postgres:16-alpine`)**:
   * Monta automáticamente el script DDL [docker/postgres/init.sql](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker/postgres/init.sql) en `/docker-entrypoint-initdb.d/init.sql:ro` para crear la tabla inmutable `operator_decision_audit` y sus índices al arrancar.
   * **Seguridad de Puerto (`127.0.0.1:5432:5432`)**: Atado exclusivamente al loopback local para que nadie desde Internet pueda conectarse al puerto `5432` (evitando que Docker se salte el firewall UFW).
   * **Healthcheck activo**: Ejecuta `pg_isready -U iot_admin -d mantenimiento_db` cada 10s.
2. **`zookeeper` y `kafka` (`confluentinc/cp-kafka:7.6.1`)**:
   * Configura **`KAFKA_NUM_PARTITIONS: 3`** ([línea 62](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker-compose.yml#L62)) para que todos los tópicos (`sensor_data`, `alerts_warning`, `alerts_critical`) tengan **3 particiones** y permitan paralelismo manteniendo el orden por clave (`sensorId`).
   * Puertos confinados en `127.0.0.1:2181` y `127.0.0.1:9092`.
3. **`rabbitmq` (Imagen personalizada con Plugin `x-delayed-message`)**:
   * Construye desde [docker/rabbitmq/Dockerfile](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker/rabbitmq/Dockerfile), el cual descarga y habilita el plugin oficial **`rabbitmq_delayed_message_exchange`** (`rabbitmq-plugins enable --offline rabbitmq_delayed_message_exchange`).
   * Elimina el usuario inseguro por defecto `guest/guest` y configura credenciales propias (`iot_rabbit` / `iot_rabbit_2026`), confinando `5672` y `15672` en `127.0.0.1`.

### 5.2. Bloque 2: Los 8 Microservicios Backend y Escalado Horizontal con Réplicas ([Líneas 105 a 334](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker-compose.yml#L105-L334))
1. **Arranque Ordenado por Salud (`depends_on: condition: service_healthy`)**:
   * Ningún microservicio arranca a ciegas: esperan a que los *healthchecks* de `postgres`, `kafka` y `rabbitmq` estén en estado `healthy`.
2. **Escalado Horizontal Real (`deploy: replicas: 2`)**:
   * En **`actuator_worker`** ([línea 299](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker-compose.yml#L299)) y **`maintenance_worker`** ([línea 327](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker-compose.yml#L327)) definimos `replicas: 2`. Docker levanta **2 contenedores concurrentes** para cada worker (`finalfinal-actuator_worker-1` y `-2`, `finalfinal-maintenance_worker-1` y `-2`) que consumen las colas de RabbitMQ en balanceo *Round-Robin*.
3. **Puente Seguro hacia el Demonio Amazon SNS del Host (`extra_hosts`)**:
   * En `alert_router` ([línea 178](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker-compose.yml#L178)) y `action_dispatcher` ([línea 269](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker-compose.yml#L269)) se inyecta `host.docker.internal:host-gateway` para que puedan invocar el servicio local `scada-sns-bridge.service` (`:9099`) sin salir a Internet.
4. **Control de Recursos (`cpus` y `memory`) y `Dockerfile` Multi-Etapa sin Root**:
   * Cada contenedor tiene límites estrictos de CPU (`0.25` a `1.00 vCPU`) y RAM (`128M` a `768M`) para evitar que un pico de carga agote la instancia EC2.
   * Todos los `Dockerfile` de los microservicios compilan con `maven:3.9.6-eclipse-temurin-17-alpine` en la Etapa 1 y ejecutan en la Etapa 2 sobre `eclipse-temurin:17-jre-alpine` con un usuario sin privilegios (`USER appuser`) y `-XX:MaxRAMPercentage=75.0`.

### 5.3. Bloque 3: Único Punto de Entrada Público (`frontend_dashboard` — Nginx Reverse Proxy) ([Líneas 338 a 364](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/docker-compose.yml#L338-L364))
* Construye la SPA **React 18 + TypeScript + Gemelo Digital 3D** en la Etapa 1 de [Frontend/Dockerfile](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Frontend/Dockerfile) y la sirve con **Nginx 1.27 Alpine** en la Etapa 2.
* Es el **único contenedor que publica los puertos `80` y `443`** al exterior. En [Frontend/nginx.conf](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Frontend/nginx.conf) actúa como **Reverse Proxy** con cifrado TLS 1.2/1.3, cabeceras de defensa (`HSTS`, `X-Frame-Options: DENY`, `nosniff`), *Rate Limiting* y resolución DNS dinámica interna (`resolver 127.0.0.11 valid=10s`) hacia la red privada `iot_industrial_net`.

---

## 6. ¿Qué Hicimos en los Archivos `.sh` de Despliegue y Hardening para Linux?

En la carpeta `aws/` creamos dos scripts automatizados e idempotentes en Bash para Ubuntu Server 24.04 LTS:

### 6.1. Script 1: [aws/deploy-aws.sh](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/deploy-aws.sh) (Aprovisionamiento Base y Despliegue Inicial)
Este script prepara una máquina virtual recién creada en AWS EC2 en **7 pasos**:
* **Paso 1**: Actualiza los repositorios `apt` e instala utilidades de red y criptografía (`curl`, `git`, `ca-certificates`, `gnupg`, `ufw`).
* **Paso 2 (Crítico para la Capa Gratuita)**: Crea y activa un archivo de **Memoria Virtual Swap de 6 GB (`/swapfile`)** sobre el disco SSD EBS (`fallocate -l 6G /swapfile`, `chmod 600`, `mkswap`, `swapon`) y lo registra en `/etc/fstab` con `vm.swappiness=60`. Esto permite que una instancia pequeña de la capa gratuita corra los 15 contenedores con total fluidez.
* **Paso 3**: Configura en el kernel `vm.max_map_count=262144`, requisito indispensable para el mapeo de memoria de Apache Kafka.
* **Paso 4**: Instala **Docker Engine** y **Docker Compose v2** desde el repositorio oficial firmado de Docker y habilita el servicio en `systemd`.
* **Paso 5**: Configura la política base del firewall **UFW** (`default deny incoming`, `limit 22/tcp`, `allow 80/tcp`, `allow 443/tcp`).
* **Paso 6**: Descarga las imágenes base y compila/levanta los 15 contenedores con `sudo docker compose up -d --build`.
* **Paso 7**: Invoca automáticamente `./aws/hardening-ubuntu.sh`.

### 6.2. Script 2: [aws/hardening-ubuntu.sh](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh) (Blindaje de Sistemas Operativos en 12 Fases + Integración AWS Cloud)
Este script implementa la estrategia de **Defensa en Profundidad** sobre Ubuntu Server 24.04 LTS y conecta el servidor con los servicios nativos de AWS:

| Fase en `hardening-ubuntu.sh` | Archivos del Sistema que Configura | ¿Qué hace exactamente en el Servidor Linux? |
| :--- | :--- | :--- |
| **Fase 1: Parches Automáticos** ([Líneas 14-27](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L14-L27)) | `/etc/apt/apt.conf.d/20auto-upgrades` | Instala y activa `unattended-upgrades`, `needrestart`, `auditd`, `fail2ban`, `sysstat`, `lynis` y `python3-boto3`. |
| **Fase 2: Blindaje SSH, Sudo y PAM** ([Líneas 29-91](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L29-L91)) | `/etc/issue.net`<br>`/etc/ssh/sshd_config.d/01-hardening.conf`<br>`/etc/sudoers.d/99-hardening`<br>`/etc/security/pwquality.conf`<br>`/etc/security/faillock.conf` | Coloca el banner legal disuasorio, deshabilita login de `root` y contraseñas en SSH (`PasswordAuthentication no`, `MaxAuthTries 3`), exige `use_pty` y `timestamp_timeout=5` en `sudo`, e impone contraseñas de 14 caracteres y bloqueo PAM tras 5 fallos. |
| **Fase 3: Firewall UFW + IPS Fail2ban** ([Líneas 93-130](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L93-L130)) | `/etc/fail2ban/jail.local` | Elimina cualquier puerto interno residual en UFW y activa **Fail2ban** con cárceles `sshd` (modo agresivo) y `recidive` (baneo incremental hasta 1 semana para atacantes reincidentes). |
| **Fase 4: Hardening del Kernel Linux** ([Líneas 132-186](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L132-L186)) | `/etc/sysctl.d/99-hardening.conf`<br>`/etc/systemd/coredump.conf.d/disable.conf`<br>`/etc/modprobe.d/99-hardening.conf` | Activa ASLR nivel 2 (`randomize_va_space=2`), oculta punteros de kernel (`kptr_restrict=2`), activa `tcp_syncookies=1`, bloquea redirecciones ICMP, desactiva volcados de memoria (*Core Dumps*) y bloquea módulos USB/sistemas de archivos obsoletos. |
| **Fase 5: Vigilancia de Recursos** ([Líneas 188-209](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L188-L209)) | `/usr/local/sbin/check-resources.sh`<br>`/etc/cron.d/check-resources` | Ejecuta cada 5 minutos una auditoría de CPU, RAM y Disco; si superan el umbral (85%/90%), registra en `syslog` los procesos causantes. |
| **Fase 6: Auditoría Forense `auditd`** ([Líneas 211-235](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L211-L235)) | `/etc/audit/rules.d/99-hardening.rules`<br>`/home/ubuntu/suid_baseline.txt` | Monitorea en el kernel cualquier escritura en `/etc/passwd`, `/etc/shadow`, `/etc/sudoers`, `/etc/ssh` y ejecuciones de `sudo`/`su`, y guarda la línea base de binarios SUID/SGID. |
| **Fase 7: Demonio Confinado Amazon SNS** ([Líneas 237-328](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L237-L328)) | `/usr/local/sbin/scada-sns-bridge.py`<br>`/etc/systemd/system/scada-sns-bridge.service` | Crea el puente local hacia **Amazon SNS (`scada-iot-alertas-ucc`)** usando credenciales temporales del **Rol IAM (`IMDSv2`)**, confinado en `systemd` con `NoNewPrivileges=true`, `ProtectSystem=strict`, `PrivateTmp=true` y `MemoryMax=128M`. |
| **Fase 8: Backups PostgreSQL SHA-256 + AWS S3** ([Líneas 330-436](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L330-L436)) | `/usr/local/sbin/backup-postgres-scada.sh`<br>`/usr/local/sbin/upload-backup-s3.py`<br>`/etc/cron.daily/backup-postgres-scada` | Realiza un `pg_dump` diario comprimido (`.sql.gz`), calcula su firma **`SHA-256`** y lo sube cifrado con **`AES-256`** al bucket privado **`s3://scada-ucc-backups-148395526595`**. |
| **Fase 9: Telemetría a AWS CloudWatch** ([Líneas 438-488](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L438-L488)) | `/usr/local/sbin/push-cloudwatch-metrics.py`<br>`/etc/cron.d/scada-cloudwatch` | Envía cada 5 minutos las métricas de uso de RAM, Swap, Disco y Contenedores activos al Namespace **`SCADA/IndustrialIoT`** en AWS CloudWatch. |
| **Fase 10: Registro en AWS ACM** ([Líneas 490-536](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L490-L536)) | `/usr/local/sbin/register-acm-cert.py` | Importa y registra oficialmente el certificado TLS del servidor dentro de **AWS Certificate Manager (ACM)** en `us-east-2`. |
| **Fase 11: Auto-Deploy GitOps desde GitHub** ([Líneas 538-563](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L538-L563)) | `/usr/local/sbin/scada-autodeploy.sh`<br>`/etc/cron.d/scada-autodeploy` | Consulta cada 2 minutos la rama `main` de GitHub; si detecta un nuevo commit, actualiza el código y recompila los contenedores automáticamente. |
| **Fase 12: Sincronización al Encender EC2** ([Líneas 565-645](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/aws/hardening-ubuntu.sh#L565-L645)) | `/usr/local/sbin/scada-boot-sync.sh`<br>`/etc/systemd/system/scada-boot-sync.service` | Al apagar y volver a prender la instancia EC2, levanta los 15 contenedores, detecta la nueva IP pública asignada por AWS y actualiza automáticamente **Amazon CloudFront (`https://dnzhr4v1oxvn7.cloudfront.net`)**. |

---

## 7. ¿Cómo se Utilizaron los `record` de Java 17 y sus Constructores Compactos para Hacer Validaciones en el Backend?

**¡Sí!** En los **8 microservicios** utilizamos la característica moderna de **Java 17 `record` con Constructor Compacto (`public NombreRecord { ... }`)** para implementar **Value Objects Inmutables con Validación *Fail-Fast*** (es decir, es imposible instanciar en memoria un objeto con datos inválidos, nulos o fuera de rango físico).

Aquí están los **6 `record` principales con validaciones de negocio** que puedes mostrar al profesor:

### 7.1. `VibrationReadingRecord` (Validación de Rangos Físicos e Infinitos/`NaN`)
* **Archivo**: [VibrationReadingRecord.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/VibrationReadingRecord.java#L7-L28) (Presente en `sensor_producer`, `alert_detector` y `plant_monitor_backend`).
* **¿Qué valida su constructor compacto?**:
  1. Que `sensorId` y `timestamp` no sean nulos (`Objects.requireNonNull`).
  2. Que `vibrationValue` no sea `NaN` ni infinito (`Double.isNaN(vibrationValue) || Double.isInfinite(vibrationValue)`).
  3. Que la vibración esté dentro de los límites físicos reales de un sensor industrial (`0.0 <= vibrationValue <= 500.0 mm/s`). Si alguien intenta inyectar una vibración negativa (`-5.0`) o absurda (`9999.0`), el `record` lanza `IllegalArgumentException` inmediatamente antes de entrar a Kafka.

### 7.2. `SensorId` y `AlertId` (Validación de Identificadores No Vacíos y Serialización Plana)
* **Archivos**:
  * [SensorId.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/SensorId.java#L14-L37) (En los **8 microservicios**).
  * [AlertId.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/AlertId.java) (En `alert_detector`, `alert_router`, `operator_console_backend`, `action_dispatcher` y `plant_monitor_backend`).
* **¿Qué valida su constructor compacto?**:
  1. Verifica que el `String value` no sea `null`.
  2. Aplica `.trim()` para limpiar espacios en blanco accidentales y valida que no quede vacío (`if (trimmed.isEmpty()) throw new IllegalArgumentException(...)`).
  3. Reasigna el valor normalizado (`value = trimmed;`) y usa `@JsonValue` + `@JsonCreator` para que al viajar por JSON se serialice como texto limpio (`"sensor_A"`) en vez de un objeto anidado.

### 7.3. `HumanActionRequest` (Validación de Lista Inmutable de Opciones para el Operador)
* **Archivo**: [HumanActionRequest.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_router/src/main/java/com/empresa/mantenimiento/domain/model/HumanActionRequest.java#L14-L34) (En `alert_router` y `operator_console_backend`).
* **¿Qué valida su constructor compacto?**:
  1. Valida que `alertId`, `sensorId`, `severity`, `availableOptions` y `timestamp` no sean nulos.
  2. Verifica que la lista de opciones de mitigación no esté vacía (`if (availableOptions.isEmpty()) throw new RoutingDomainException(...)`).
  3. Hace una **copia defensiva inmutable** (`availableOptions = Collections.unmodifiableList(List.copyOf(availableOptions))`) para que ninguna capa externa pueda alterar las opciones de la alerta una vez creada.

### 7.4. `OperatorDecisionRecord` (Validación y Normalización de la Decisión del Operador)
* **Archivo**: [OperatorDecisionRecord.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/domain/model/OperatorDecisionRecord.java#L10-L31) (En `action_dispatcher`).
* **¿Qué valida su constructor compacto?**:
  1. Exige nulidad cero en `alertId`, `sensorId`, `chosenAction`, `operatorId` y `timestamp`.
  2. Verifica que `chosenAction` no esté en blanco y la normaliza automáticamente a mayúsculas (`chosenAction = chosenAction.trim().toUpperCase()`).
  3. Si `notes` llega `null`, la convierte de forma segura a cadena vacía `""` para evitar `NullPointerException` al guardar en PostgreSQL.

### 7.5. `ShutdownCommand` y `DetectionResult` (Validación de Comandos Críticos y Resultados de Estrategia)
* **Archivos**:
  * [ShutdownCommand.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/action_dispatcher/src/main/java/com/empresa/mantenimiento/domain/model/ShutdownCommand.java#L7-L21) (En `action_dispatcher` y `actuator_worker`): Valida en su constructor compacto que ninguna orden de apagado de emergencia pueda crearse sin `commandId`, `sensorId`, `reason`, `initiatedBy` e `issuedAt`.
  * [DetectionResult.java](file:///c:/Users/RealMix1/Desktop/TrabajoFinalPoo_SD_SO/Backend/alert_detector/src/main/java/com/empresa/mantenimiento/domain/model/strategy/DetectionResult.java#L7-L28) (En `alert_detector`): Valida que el resultado devuelto por cualquier estrategia (`RuleBasedThresholdStrategy` o `ExponentialMovingAverageStrategy`) tenga siempre una `severity` y una `explanation` no nulas, exponiendo además *Factory Methods* semánticos (`DetectionResult.normal(...)`, `DetectionResult.warning(...)`, `DetectionResult.critical(...)`).


