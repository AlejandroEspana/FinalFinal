# Diagrama de Despliegue UML — Infraestructura Cloud en AWS EC2 con Defensa en Profundidad

Este documento describe la arquitectura física y lógica de despliegue en **Amazon Web Services (AWS — Región `us-east-2` Ohio)** sobre una instancia **Ubuntu Server 24.04 LTS**, integrando el blindaje de Sistemas Operativos (Hardening), aislamiento de puertos en `127.0.0.1`, cifrado TLS 1.2/1.3 y servicios nativos de AWS (**Amazon SNS**, **Amazon S3** y **Amazon CloudWatch**).

---

## 1. Diagrama de Despliegue en Producción (Mermaid)

```mermaid
flowchart TB
    subgraph INTERNET["🌐 Red Pública / Internet"]
        USER["💻 Navegador del Operador SCADA\nhttps://dnzhr4v1oxvn7.cloudfront.net"]
        EMAIL["📧 Correo Institucional & Móvil\nalejandro.espaname@campusucc.edu.co\n+573154613867"]
    end

    subgraph AWS_CLOUD["☁️ Amazon Web Services — Región us-east-2 (Capa Gratuita)"]
        CF_EDGE["🌍 Amazon CloudFront CDN + AWS Native TLS 1.2/1.3 (E2EU8NOYSSDYW6)\nDominio Limpio sin IPs ni Puertos: dnzhr4v1oxvn7.cloudfront.net\nCertificado Oficial: Amazon Trust Services (ACM)"]
        SG["🛡️ AWS Security Group (launch-wizard-1)\nInbound permitido: TCP 80 (HTTP), TCP 443 (HTTPS), TCP 22 (SSH)\nBloqueados al exterior: 5432, 9092, 5672, 15672, 8080-8094"]

        subgraph EC2["🖥️ Instancia AWS EC2 — Ubuntu Server 24.04 LTS (77.113.90.86)"]
            UFW["🧱 Firewall Host UFW + IPS Fail2ban + Kernel sysctl + Auditd\nPolítica Default Deny Incoming | Protección Anti-Fuerza Bruta"]

            subgraph DOCKER_NET["🐳 Red Bridge Interna Docker (iot_industrial_net — Confinada en 127.0.0.1)"]
                NGINX["🔒 iot_frontend_dashboard\nNginx 1.27 Alpine Reverse Proxy\nTLS 1.2/1.3 + HSTS + DNS Resolver (127.0.0.11)\nReact 18 + TypeScript + Gemelo 3D"]

                subgraph DATA_LAYER["Infraestructura de Mensajería y Persistencia"]
                    ZK["iot_zookeeper\nConfluent 7.6.1"]
                    KAFKA["iot_kafka\nApache Kafka 7.6.1\n(3 Particiones Stateful)"]
                    RMQ["iot_rabbitmq\nRabbitMQ 3.13 + Plugin\nrabbitmq_delayed_message_exchange"]
                    PG[("iot_postgres\nPostgreSQL 16 Alpine\nTabla: operator_decision_audit")]
                end

                subgraph MICROSERVICES["8 Microservicios Backend (Java 17 + Spring Boot 3.3.5)"]
                    PROD["iot_sensor_producer\nSimulador Realista (12s)"]
                    DET["iot_alert_detector\nVentana Móvil + Patrón State/Strategy"]
                    ROUT["iot_alert_router\nGenerador de Opciones + Fanout"]
                    PMON["iot_plant_monitor_backend\nEstado de Planta + WebSocket STOMP"]
                    OCON["iot_operator_console_backend\nCola de Alertas + WebSocket STOMP"]
                    DISP["iot_action_dispatcher\nSpring Security + Auditoría ACID + Outbox"]
                    ACT["actuator_worker (x2 Réplicas)\nParadas de Emergencia"]
                    MAINT["maintenance_worker (x2 Réplicas)\nÓrdenes Inmediatas y Diferidas x-delay"]
                end
            end

            subgraph HOST_SERVICES["⚙️ Servicios Confinados del Sistema Operativo (Systemd / Cron)"]
                SNS_BRIDGE["scada-sns-bridge.service\nDemonio Python3 Boto3 (IMDSv2)\nMemoryMax=128M | ProtectSystem=strict"]
                BACKUP_CRON["backup-postgres-scada.sh\nCron Diario: pg_dump + gzip + SHA-256\n/var/backups/scada-postgres"]
                CW_CRON["push-cloudwatch-metrics.py\nCron 5m: RAM, Swap, Disco y Contenedores"]
            end
        end

        IAM["🔑 Rol IAM Adjunto (IMDSv2)\nec2-scada-sns-role\n(Sin llaves estáticas en disco)"]
        SNS_TOPIC["📢 Amazon SNS Topic\nscada-iot-alertas-ucc"]
        S3_BUCKET[("🪣 Amazon S3 Bucket Privado\nscada-ucc-backups-148395526595\n(Cifrado AES-256 + Block Public Access)")]
        CW_SVC["📈 Amazon CloudWatch\nNamespace: SCADA/IndustrialIoT"]
    end

    %% Conexiones externas
    USER ==>|"HTTPS (AWS TLS 1.3) / WSS"| CF_EDGE
    CF_EDGE ==>|"Origin Proxy"| SG
    SG ==> UFW
    UFW ==> NGINX

    %% Enrutamiento interno Nginx (Sin exponer puertos al cliente)
    NGINX -->|"Proxy Interno /api/simulation"| PROD
    NGINX -->|"Proxy Interno /api/detector"| DET
    NGINX -->|"Proxy Interno /api/plant & /ws-plant"| PMON
    NGINX -->|"Proxy Interno /api/alerts & /ws-alerts"| OCON
    NGINX -->|"Proxy Interno /api/dispatcher"| DISP
    NGINX -->|"Round-Robin /api/actuators"| ACT
    NGINX -->|"Round-Robin /api/maintenance"| MAINT

    %% Flujo Kafka
    ZK <--> KAFKA
    PROD -->|"Publica sensor_data (Key=sensorId)"| KAFKA
    KAFKA -->|"Consume sensor_data"| DET
    KAFKA -->|"Consume sensor_data"| PMON
    DET -->|"Publica alerts_critical / alerts_warning"| KAFKA
    KAFKA -->|"Consume alertas"| ROUT

    %% Flujo RabbitMQ y PostgreSQL
    ROUT -->|"Fanout: human_alerts"| RMQ
    RMQ -->|"human_alerts_console_queue"| OCON
    DISP ==>|"1. Transacción ACID"| PG
    DISP ==>|"2. Immediate / Delayed (x-delay)"| RMQ
    RMQ -->|"critical_actions_queue"| ACT
    RMQ -->|"maintenance_queue"| MAINT

    %% Integración con Servicios del Host y AWS Cloud
    ROUT -.->|"Boletín Crítico ISO-10816"| SNS_BRIDGE
    DISP -.->|"Acta de Resolución ISO-10816"| SNS_BRIDGE
    PG -.->|"Volcado Diario pg_dump"| BACKUP_CRON

    SNS_BRIDGE --> IAM
    BACKUP_CRON --> IAM
    CW_CRON --> IAM

    IAM ==>|"sns:Publish"| SNS_TOPIC
    IAM ==>|"s3:PutObject (AES256)"| S3_BUCKET
    IAM ==>|"cloudwatch:PutMetricData"| CW_SVC
    SNS_TOPIC ==>|"Email Corporativo & SMS"| EMAIL
```

---

## 2. Tabla de Componentes de Despliegue y Confinamiento de Red

| Componente / Servicio | Contenedor / Unidad | Exposición Pública | Red / Mecanismo de Acceso | Límite de Recursos |
| :--- | :--- | :--- | :--- | :--- |
| **Reverse Proxy & SPA 3D** | `iot_frontend_dashboard` | **Sí** (`HTTP` / `HTTPS`) | Único punto de entrada público con TLS 1.2/1.3, HSTS y Rate Limiting | `0.25 vCPU` / `128 MB` |
| **PostgreSQL 16** | `iot_postgres` | **No** (Confinado) | Red interna `iot_industrial_net` + Loopback `127.0.0.1` | `0.50 vCPU` / `512 MB` |
| **Apache Kafka + Zookeeper** | `iot_kafka`, `iot_zookeeper` | **No** (Confinado) | Red interna `iot_industrial_net` (3 particiones por `sensorId`) | `1.50 vCPU` / `1280 MB` |
| **RabbitMQ 3.13 + Delayed** | `iot_rabbitmq` | **No** (Confinado) | Red interna `iot_industrial_net` (credenciales personalizadas) | `0.50 vCPU` / `512 MB` |
| **8 Microservicios Spring Boot** | `iot_*` + réplicas workers | **No** (Confinado) | Accesibles únicamente desde Nginx vía DNS interno `127.0.0.11` | `0.50 vCPU` / `384-512 MB` c/u |
| **Puente Amazon SNS** | `scada-sns-bridge.service` | **No** (Confinado) | Servicio `systemd` local con `ProtectSystem=strict` + Rol IAM `IMDSv2` | `0.50 vCPU` / `128 MB` |
| **Respaldo Criptográfico** | `backup-postgres-scada.sh` | **No** (Local + S3) | Cron diario con `gzip -9`, firma `SHA-256` y subida cifrada a AWS S3 | Ejecución programada |
