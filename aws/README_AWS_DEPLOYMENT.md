# Guía Completa de Despliegue en la Nube: AWS EC2
## Plataforma de Mantenimiento Predictivo Interactivo (IoT Industrial)

Esta guía detalla el procedimiento paso a paso para aprovisionar, configurar y desplegar toda la arquitectura de microservicios contenerizados (Kafka, RabbitMQ, PostgreSQL, Java Spring Boot y Nginx) sobre una máquina virtual en **Amazon Web Services (AWS)**.

---

## 1. Dimensionamiento de la Instancia EC2

Dado que la plataforma orquesta **12+ contenedores simultáneos** (Zookeeper, Kafka, RabbitMQ, PostgreSQL, 6 microservicios Spring Boot, 4 workers en réplica y Nginx) con cuotas de recursos asignadas (`deploy.resources.limits`), se recomienda el siguiente tipo de instancia:

| Parámetro | Valor Recomendado | Valor Mínimo para Pruebas |
|---|---|---|
| **Tipo de Instancia** | `t3.xlarge` (o `c6i.xlarge` / `m6i.xlarge`) | `t3.large` |
| **vCPU** | 4 vCPU | 2 vCPU |
| **Memoria RAM** | 16 GB RAM | 8 GB RAM (ajustando réplicas a 1) |
| **Almacenamiento** | 40 GB gp3 SSD | 30 GB gp3 SSD |
| **Sistema Operativo** | Ubuntu Server 22.04 LTS o 24.04 LTS | Ubuntu Server 22.04 LTS |

> **Nota:** La instancia `t3.xlarge` garantiza que los límites ergonómicos de la JVM (`MaxRAMPercentage=75%`) y los recolectores de basura operen sin saturar el host ante flujos masivos de telemetría.

---

## 2. Configuración del Grupo de Seguridad (Security Group) en AWS

En la consola de AWS EC2, cree un **Security Group** con las siguientes reglas de entrada (**Inbound Rules**):

| Tipo | Protocolo | Rango de Puertos | Origen | Propósito |
|---|---|---|---|---|
| **SSH** | TCP | `22` | Tu IP Pública (`Mi IP`) | Acceso seguro a la terminal |
| **Custom TCP** | TCP | `3000` | `0.0.0.0/0` | SCADA Dashboard (Frontend Nginx) |
| **Custom TCP** | TCP | `8080` | `0.0.0.0/0` | API REST action_dispatcher (Spring Security) |
| **Custom TCP** | TCP | `8081` | `0.0.0.0/0` | WebSocket plant_monitor_backend |
| **Custom TCP** | TCP | `8082` | `0.0.0.0/0` | WebSocket operator_console_backend |
| **Custom TCP** | TCP | `15672` | Tu IP Pública (`Mi IP`) | Consola de Administración RabbitMQ |

> **Seguridad:** Los puertos internos de infraestructura (`9092` Kafka, `5672` RabbitMQ AMQP, `5432` PostgreSQL, `2181` Zookeeper) **NO** deben exponerse públicamente hacia Internet; se comunican de forma aislada a través de la red virtual de Docker (`industrial_iot_net`).

---

## 3. Conexión y Clonación del Repositorio

1. Conéctese a su instancia EC2 vía SSH usando su archivo de llave privada `.pem`:
   ```bash
   ssh -i "su-llave.pem" ubuntu@<IP_PUBLICA_EC2>
   ```

2. Transfiera o clone el código fuente en el directorio principal:
   ```bash
   git clone <URL_DE_SU_REPOSITORIO> ~/TrabajoFinalPoo_SD_SO
   cd ~/TrabajoFinalPoo_SD_SO
   ```

---

## 4. Despliegue Automatizado con un Solo Comando

Ejecute el script de aprovisionamiento automatizado:
```bash
chmod +x aws/deploy-aws.sh
./aws/deploy-aws.sh
```

El script se encargará automáticamente de:
- Actualizar dependencias del sistema operativo Ubuntu.
- Configurar límites del kernel para Kafka (`vm.max_map_count=262144`).
- Instalar Docker Engine y el plugin Docker Compose oficial.
- Configurar el firewall local (UFW).
- Compilar las imágenes Docker multietapa en paralelo y levantar la arquitectura completa.

---

## 5. Verificación del Estado del Clúster en AWS

Una vez finalizado el script, verifique que todos los contenedores y réplicas se encuentren en estado `healthy` o `Up`:

```bash
docker compose ps
```

Salida esperada:
```text
NAME                       IMAGE                             STATUS                   PORTS
action-dispatcher          trabajofinalpoo_sd_so-action-dispatcher    Up (healthy)             0.0.0.0:8080->8080/tcp
actuator-worker-1          trabajofinalpoo_sd_so-actuator-worker      Up                       8085/tcp
actuator-worker-2          trabajofinalpoo_sd_so-actuator-worker      Up                       8085/tcp
alert-detector             trabajofinalpoo_sd_so-alert-detector       Up                       8088/tcp
alert-router               trabajofinalpoo_sd_so-alert-router         Up                       8087/tcp
frontend-scada             nginx:alpine                               Up                       0.0.0.0:3000->80/tcp
kafka                      confluentinc/cp-kafka:7.5.0               Up (healthy)             0.0.0.0:9092->9092/tcp
maintenance-worker-1       trabajofinalpoo_sd_so-maintenance-worker   Up                       8086/tcp
maintenance-worker-2       trabajofinalpoo_sd_so-maintenance-worker   Up                       8086/tcp
operator-console-backend   trabajofinalpoo_sd_so-operator-console-backend Up                   0.0.0.0:8082->8082/tcp
plant-monitor-backend      trabajofinalpoo_sd_so-plant-monitor-backend   Up                   0.0.0.0:8081->8081/tcp
postgres-db                postgres:16-alpine                         Up (healthy)             0.0.0.0:5432->5432/tcp
rabbitmq                   trabajofinalpoo_sd_so-rabbitmq             Up (healthy)             0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
sensor-producer            trabajofinalpoo_sd_so-sensor-producer      Up                       8089/tcp
zookeeper                  confluentinc/cp-zookeeper:7.5.0           Up                       2181/tcp
```

---

## 6. Acceso al Frontend y Prueba Operativa

1. Abra su navegador web e ingrese a:
   ```text
   http://<IP_PUBLICA_EC2>:3000/dashboard.html
   ```
2. Observe en tiempo real:
   - La telemetría continua de los 10 sensores consumidos desde **Apache Kafka**.
   - La activación de las alertas interactivas cuando se superan los umbrales (Crítico > 90 o Advertencia > 75 x3) mediante **RabbitMQ Fanout Exchange**.
   - La pulsación de los botones de acción (`APAGADO_INMEDIATO`, `PROGRAMAR_MANTENIMIENTO_AHORA`, `RECONOCER_Y_ESPERAR_24H`), enviando la petición asegurada con **Spring Security** al endpoint `/api/v1/decide`.
   - La persistencia transaccional inmediata en **PostgreSQL** y la delegación asíncrona a los workers correspondientes (`actuator_worker` o `maintenance_worker`).

---

## 7. Comandos de Operación y Diagnóstico

- **Ver logs en tiempo real de un microservicio:**
  ```bash
  docker compose logs -f action-dispatcher
  docker compose logs -f actuator-worker
  docker compose logs -f maintenance-worker
  ```

- **Inspeccionar registros de auditoría en PostgreSQL:**
  ```bash
  docker compose exec postgres-db psql -U postgres -d predictive_db -c "SELECT id, alert_id, sensor_id, chosen_action, routing_target, recorded_at FROM operator_decision_audit ORDER BY id DESC LIMIT 10;"
  ```

- **Detener la plataforma:**
  ```bash
  docker compose down
  ```

- **Detener y limpiar volúmenes de datos:**
  ```bash
  docker compose down -v
  ```
