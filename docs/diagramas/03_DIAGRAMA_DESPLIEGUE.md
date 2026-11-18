# 03. Diagramas de Despliegue UML — Arquitectura Cloud en AWS con Defensa en Profundidad

El despliegue en producción se divide en **2 diagramas complementarios** para facilitar su lectura:
1. **Topología Perimetral, Seguridad de Sistema Operativo Ubuntu 24.04 LTS y Servicios Cloud AWS**.
2. **Topología Interna de Contenedores Docker, Buses de Mensajería (Kafka y RabbitMQ) y Persistencia (PostgreSQL)**.

---

## 1. Vista Externa: Red de Borde AWS CloudFront, Hardening Ubuntu Server y Servicios Cloud

```mermaid
flowchart TB
    subgraph CLIENTE["1. Capa de Acceso del Operador"]
        BROWSER["💻 Navegador Web (Consola SCADA & Gemelo 3D)\nhttps://dnzhr4v1oxvn7.cloudfront.net"]
        CORREO["📧 Correo Institucional & Móvil de Guardia\nalejandro.espaname@campusucc.edu.co | +573154613867"]
        GITHUB["🐙 Repositorio GitHub (Rama main)\nAlejandroEspana/FinalFinal"]
    end

    subgraph AWS_EDGE["2. Capa de Borde y Seguridad Perimetral AWS (Región us-east-2)"]
        CF["🌍 Amazon CloudFront CDN (ID: E2EU8NOYSSDYW6)\nDominio Limpio sin IPs ni Puertos + Certificado TLS Nativo AWS"]
        ACM["🔐 AWS Certificate Manager (ACM)\nCertificado Registrado: 3dd292c0-59c8-41e1-bc78-c3eccfdf5597"]
        SG["🛡️ AWS Security Group (launch-wizard-1)\nSolo permite entrada en TCP 80, 443 y 22"]
    end

    subgraph EC2_HOST["3. Instancia AWS EC2 — Ubuntu Server 24.04 LTS (6 GB Swap SSD)"]
        UFW["🧱 Firewall Host UFW + IPS Fail2ban + Kernel sysctl (ASLR) + Auditd\nPolítica Default Deny Incoming | Cero Puertos Internos Expuestos"]
        NGINX["🔒 Contenedor: iot_frontend_dashboard (Nginx 1.27 Alpine)\nReverse Proxy + TLS 1.2/1.3 + HSTS + DNS Resolver Dinámico (127.0.0.11)"]

        subgraph SYSTEMD_DAEMONS["Demonios Confinados del Sistema Operativo (Systemd / Cron)"]
            BRIDGE["scada-sns-bridge.service\nPuente Local Python3 Boto3\n(Confinado en 127.0.0.1:9099)"]
            BACKUP["backup-postgres-scada.sh\nVolcado Diario pg_dump + gzip\nFirma Criptográfica SHA-256"]
            CW_AGENT["push-cloudwatch-metrics.py\nTelemetría cada 5 min:\nRAM, Swap, Disco y Docker"]
            AUTODEPLOY["scada-autodeploy.sh + scada-boot-sync.sh\nSincronización Automática con GitHub main\ny Actualización de IP en CloudFront al Iniciar"]
        end
    end

    subgraph AWS_SERVICES["4. Servicios Administrados de AWS (vía Rol IAM ec2-scada-sns-role / IMDSv2)"]
        SNS["📢 Amazon SNS Topic\nscada-iot-alertas-ucc"]
        S3[("🪣 Amazon S3 Bucket Privado\nscada-ucc-backups-148395526595\n(Cifrado AES-256)")]
        CW["📈 Amazon CloudWatch\nNamespace: SCADA/IndustrialIoT"]
    end

    BROWSER ==>|"HTTPS / WSS (Sin IPs ni Puertos)"| CF
    ACM -.->|"Certificado TLS"| CF
    CF ==>|"Tráfico Filtrado"| SG
    SG ==> UFW
    UFW ==> NGINX

    GITHUB -.->|"Git Pull Automático (Cada 2 min)"| AUTODEPLOY
    BRIDGE ==>|"sns:Publish"| SNS
    SNS ==>|"Boletín ISO 10816-3"| CORREO
    BACKUP ==>|"s3:PutObject (AES256 + SHA256)"| S3
    CW_AGENT ==>|"cloudwatch:PutMetricData"| CW
```

---

## 2. Vista Interna: Red Privada Docker (`iot_industrial_net` — Confinada en `127.0.0.1`)

Todos los microservicios y motores de datos corren dentro de la red bridge aislada `iot_industrial_net` con usuarios sin privilegios (`appuser`) y límites estrictos de CPU y memoria RAM.

```mermaid
flowchart TB
    NGINX["🔒 iot_frontend_dashboard (Nginx Reverse Proxy)\nÚnico contenedor con puertos publicados al exterior (80 / 443)"]

    subgraph FLUJO_KAFKA["Bus de Eventos de Alta Ingesta — Apache Kafka (3 Particiones Stateful)"]
        PROD["📡 iot_sensor_producer\nGenerador de Telemetría (12s)\nInyector Manual de Fallas"]
        KAFKA[["⚡ iot_kafka + iot_zookeeper\nTópicos: sensor_data,\nalerts_warning, alerts_critical\n(Particionados por Key = sensorId)"]]
        DET["🧠 iot_alert_detector\nVentana Móvil Stateful\nPatrones State + Strategy"]
        PMON["🖥️ iot_plant_monitor_backend\nEstado de los 10 Sensores\nPush WebSocket (/ws-plant)"]
    end

    subgraph FLUJO_RABBIT["Bus de Decisiones y Tareas Diferidas — RabbitMQ 3.13"]
        ROUT["🔀 iot_alert_router\nGenerador de Opciones Dinámicas"]
        RMQ[["🐇 iot_rabbitmq\n1. Fanout: human_alerts\n2. Direct: immediate_actions_exchange\n3. Delayed: delayed_actions_exchange (x-delay)"]]
        OCON["🚨 iot_operator_console_backend\nCola: human_alerts_console_queue\nPush WebSocket (/ws-alerts)"]
        DISP["⚖️ iot_action_dispatcher\nSpring Security (Basic Auth)\nSincronización afterCommit()"]
    end

    subgraph PERSISTENCIA_WORKERS["Persistencia Transaccional ACID y Workers Concurrentes"]
        PG[("🗄️ iot_postgres (PostgreSQL 16)\nTabla: operator_decision_audit\n(Persistencia Formal Inmutable)")]
        ACT["🛑 actuator_worker (2 Réplicas)\nCola: critical_actions_queue\n(Corte de Energía Inmediato)"]
        MAINT["🔧 maintenance_worker (2 Réplicas)\nCola: maintenance_queue\n(Órdenes Inmediatas y Diferidas x-delay)"]
    end

    %% Rutas desde Nginx Reverse Proxy
    NGINX -->|"/api/simulation/*"| PROD
    NGINX -->|"/api/detector/*"| DET
    NGINX -->|"/api/plant/* & /ws-plant"| PMON
    NGINX -->|"/api/alerts/* & /ws-alerts"| OCON
    NGINX -->|"/api/dispatcher/*"| DISP
    NGINX -->|"Round-Robin /api/actuators/*"| ACT
    NGINX -->|"Round-Robin /api/maintenance/*"| MAINT

    %% Conexiones Kafka
    PROD ==>|"1. Publica sensor_data"| KAFKA
    KAFKA ==>|"2a. Consume por partición"| PMON
    KAFKA ==>|"2b. Consume por partición"| DET
    DET ==>|"3. Publica en transición"| KAFKA
    KAFKA ==>|"4. Consume alertas"| ROUT

    %% Conexiones RabbitMQ y PostgreSQL
    ROUT ==>|"5. Fanout Exchange"| RMQ
    RMQ ==>|"6. Entrega alerta"| OCON
    DISP ==>|"7. COMMIT Transaccional ACID"| PG
    DISP ==>|"8. Despacha tras Commit"| RMQ
    RMQ ==>|"9a. Parada Crítica"| ACT
    RMQ ==>|"9b. Inmediato o tras expirar x-delay"| MAINT
```
