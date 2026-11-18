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
