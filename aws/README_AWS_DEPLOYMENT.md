# Guía Completa de Despliegue en la Nube: AWS EC2
## Plataforma de Mantenimiento Predictivo Interactivo (IoT Industrial)

Esta guía detalla el procedimiento paso a paso para aprovisionar, configurar y desplegar toda la arquitectura de microservicios contenerizados (Kafka, RabbitMQ, PostgreSQL 16, 8 microservicios Java Spring Boot y Frontend Nginx) sobre **Amazon Web Services (AWS EC2)**.

---

## 1. Dimensionamiento de la Instancia EC2

Dado que la plataforma orquesta **15 contenedores simultáneos** (Zookeeper, Kafka, RabbitMQ, PostgreSQL, 6 microservicios Spring Boot centrales, 4 réplicas de workers y Nginx) con cuotas de recursos asignadas (`deploy.resources.limits`), se recomienda:

| Parámetro | Valor Recomendado | Valor Mínimo (con Swap 4GB incluido en script) |
|---|---|---|
| **Tipo de Instancia** | `t3.xlarge` | `t3.large` (8 GB RAM) |
| **vCPU** | 4 vCPU | 2 vCPU |
| **Memoria RAM** | 16 GB RAM | 8 GB RAM |
| **Almacenamiento** | 35 GB gp3 SSD | 25 GB gp3 SSD |
| **Sistema Operativo** | Ubuntu Server 24.04 LTS / 22.04 LTS | Ubuntu Server 22.04 LTS |

---

## 2. Configuración del Grupo de Seguridad (Security Group) en AWS

En la consola de AWS EC2, configura el **Security Group** con las siguientes reglas de entrada (**Inbound Rules**):

| Tipo | Protocolo | Puerto | Origen | Propósito |
|---|---|---|---|---|
| **SSH** | TCP | `22` | `Mi IP` | Acceso seguro por consola SSH |
| **Custom TCP** | TCP | `3000` | `0.0.0.0/0` | SCADA Dashboard Web (Frontend Nginx) |
| **Custom TCP** | TCP | `8080` | `0.0.0.0/0` | `action_dispatcher` API REST + Auditoría PostgreSQL |
| **Custom TCP** | TCP | `8081` | `0.0.0.0/0` | WebSocket `plant_monitor_backend` (`/ws-plant`) |
| **Custom TCP** | TCP | `8082` | `0.0.0.0/0` | WebSocket `operator_console_backend` (`/ws-alerts`) |
| **Custom TCP** | TCP | `8089` | `0.0.0.0/0` | Inyector de Telemetría `sensor_producer` |
| **Custom TCP** | TCP | `8091` | `0.0.0.0/0` | Historial `actuator_worker` |
| **Custom TCP** | TCP | `8093` | `0.0.0.0/0` | Tickets `maintenance_worker` |
| **Custom TCP** | TCP | `15672` | `Mi IP` | Consola de Administración RabbitMQ |

> **Seguridad:** Los puertos internos de infraestructura (`9092` Kafka, `5672` RabbitMQ AMQP, `5432` PostgreSQL, `2181` Zookeeper) permanecen cerrados a Internet y solo se comunican dentro de la red privada `iot_industrial_net` de Docker.

---

## 3. Conexión SSH y Clonación desde GitHub

1. Conéctate a tu instancia EC2 vía SSH:
   ```bash
   ssh -i "tu-llave.pem" ubuntu@<IP_PUBLICA_EC2>
   ```

2. Clona directamente tu repositorio de GitHub:
   ```bash
   git clone https://github.com/AlejandroEspana/FinalFinal.git
   cd FinalFinal
   ```

3. Ejecuta el instalador automatizado:
   ```bash
   chmod +x aws/deploy-aws.sh
   ./aws/deploy-aws.sh
   ```

---

## 4. Comandos Útiles de Verificación en AWS EC2

- **Ver estado de los 15 contenedores:**
  ```bash
  sudo docker compose ps
  ```

- **Consultar directamente la tabla de auditoría en PostgreSQL 16:**
  ```bash
  sudo docker compose exec postgres psql -U iot_admin -d mantenimiento_db -c "SELECT id, alert_id, sensor_id, chosen_action, status, routing_target, delay_millis, recorded_at FROM operator_decision_audit ORDER BY id DESC LIMIT 10;"
  ```
