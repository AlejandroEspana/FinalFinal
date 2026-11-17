# Manual de Pruebas, Verificación de Hardening y Comandos en la Máquina Virtual (AWS EC2)

Esta guía documenta todos los comandos y procedimientos para auditar, probar y demostrar el funcionamiento de la **Plataforma de Mantenimiento Predictivo Interactivo (IoT Industrial)** dentro del servidor de producción **Ubuntu Server 24.04 LTS en AWS EC2 (`77.113.90.86`)** y desde el navegador web.

---

## 1. Conexión Segura por SSH a la Máquina Virtual

Desde PowerShell o terminal en tu equipo local (ubicado en la carpeta del proyecto):

```powershell
ssh -i "aws-iot-key.pem" ubuntu@77.113.90.86
```

> **Verificación inmediata al conectar**: Observarás el **Banner Legal Disuasorio (`/etc/issue.net`)** configurado en la Fase 2 del Hardening antes de abrir la sesión.

---

## 2. Verificación de las Capas de Hardening de Sistemas Operativos (Ubuntu Server)

Una vez dentro de la máquina virtual (`ubuntu@ip-172-31-42-155:~$`), puedes ejecutar los siguientes comandos para demostrar cada control de seguridad ante el evaluador:

### 2.1. Verificar Confinamiento de Puertos en `127.0.0.1` (Cero Exposición de Backend/BD)
```bash
sudo ss -tulpn
```
* **Resultado esperado**:
  * Únicamente **Nginx (`80` y `443`)** y **OpenSSH (`22`)** escuchan en `0.0.0.0`.
  * **PostgreSQL (`5432`)**, **Kafka (`9092`)**, **Zookeeper (`2181`)**, **RabbitMQ (`5672`, `15672`)**, el **Puente SNS (`9099`)** y los **8 microservicios (`8080-8094`)** están estrictamente ligados a `127.0.0.1` (inaccesibles desde Internet).

### 2.2. Verificar Firewall Perimetral UFW e IPS Fail2ban
```bash
# Estado del Firewall de Host y reglas de limitación SSH
sudo ufw status verbose

# Estado de las cárceles activas en Fail2ban (sshd y recidive)
sudo fail2ban-client status
sudo fail2ban-client status sshd
```

### 2.3. Verificar Blindaje de SSH, Sudo y Políticas PAM (`pwquality` y `faillock`)
```bash
# Configuración endurecida de OpenSSH
sudo sshd -T | egrep 'permitrootlogin|passwordauthentication|maxauthtries|logingracetime|banner'

# Reglas de Sudo seguro (use_pty, timestamp_timeout=5, passwd_tries=3)
sudo cat /etc/sudoers.d/99-hardening

# Políticas de complejidad de contraseñas y bloqueo por intentos fallidos
cat /etc/security/pwquality.conf
cat /etc/security/faillock.conf
```

### 2.4. Verificar Parámetros de Seguridad del Kernel Linux (`sysctl`) y Desactivación de Core Dumps
```bash
sudo sysctl net.ipv4.tcp_syncookies \
            net.ipv4.conf.all.rp_filter \
             net.ipv4.conf.all.accept_redirects \
            kernel.randomize_va_space \
            kernel.kptr_restrict \
            kernel.dmesg_restrict \
            fs.suid_dumpable
```
* **Resultado esperado**: `randomize_va_space = 2` (ASLR total), `kptr_restrict = 2`, `dmesg_restrict = 1`, `fs.suid_dumpable = 0`.

### 2.5. Verificar Auditoría Forense del Kernel (`auditd`)
```bash
# Ver reglas cargadas de vigilancia sobre /etc/passwd, /etc/shadow, /etc/sudoers y /etc/ssh
sudo auditctl -l

# Buscar eventos recientes de escalamiento de privilegios (sudo)
sudo ausearch -k priv_esc -i | tail -n 25
```

### 2.6. Ejecutar Auditoría Integral Automatizada con Lynis
```bash
sudo lynis audit system --quick
```

---

## 3. Verificación de Respaldos Criptográficos (`SHA-256` + Amazon S3) y CloudWatch

### 3.1. Verificar Backups Locales de PostgreSQL y Firma Criptográfica `SHA-256`
```bash
# Listar respaldos comprimidos (.sql.gz) y sus firmas (.sha256)
sudo ls -lh /var/backups/scada-postgres/

# Verificar matemáticamente la integridad criptográfica SHA-256 del respaldo
sudo bash -c 'sha256sum -c /var/backups/scada-postgres/*.sha256'
```

### 3.2. Ejecutar Manualmente un Respaldo y Verificar su Subida Cifrada (`AES-256`) a Amazon S3
```bash
sudo /usr/local/sbin/backup-postgres-scada.sh
```
* **Resultado esperado**: Imprime `S3_UPLOAD_OK: s3://scada-ucc-backups-148395526595/postgres/mantenimiento_db_...sql.gz` y registra el evento en `syslog`.

### 3.3. Verificar Envío de Métricas de RAM, Swap, Disco y Contenedores a AWS CloudWatch
```bash
sudo /usr/bin/python3 /usr/local/sbin/push-cloudwatch-metrics.py
```
* **Resultado esperado**: `CLOUDWATCH_METRICS_OK: RAM=...% SWAP=...% DISK=...% CONTAINERS=15` publicando en el Namespace `SCADA/IndustrialIoT` de AWS CloudWatch.

---

## 4. Verificación de los 15 Contenedores Docker y Microservicios

### 4.1. Estado de Salud (`Healthy` / `Running`) y Uso de Recursos en Tiempo Real
```bash
cd ~/FinalFinal
sudo docker compose ps
sudo docker stats --no-stream
```

### 4.2. Inspección de Logs por Microservicio
```bash
# 1. Generador de telemetría (ciclo realista de 12s)
sudo docker logs --tail 25 iot_sensor_producer

# 2. Detector Stateful de anomalías (Patrones State + Strategy sobre Kafka)
sudo docker logs --tail 25 iot_alert_detector

# 3. Enrutador de alertas y generador de opciones hacia RabbitMQ Fanout + Amazon SNS
sudo docker logs --tail 25 iot_alert_router

# 4. Despachador de decisiones humanas, auditoría PostgreSQL y boletines ISO-10816
sudo docker logs --tail 25 iot_action_dispatcher

# 5. Workers de actuación y mantenimiento (2 réplicas cada uno)
sudo docker compose logs --tail 20 actuator_worker maintenance_worker
```

---

## 5. Pruebas Funcionales de Punta a Punta (End-to-End) por Línea de Comandos

Puedes ejecutar estas pruebas directamente dentro de la máquina virtual para verificar cada contrato JSON y cada flujo distribuido:

### Prueba 1: Verificar Cifrado TLS (`HTTPS`) y Cabeceras de Seguridad en Nginx
```bash
curl -k -I https://scada-ucc-alejandro.77.113.90.86.sslip.io
```
* **Verifica**: `HTTP/1.1 200 OK`, `Strict-Transport-Security`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`.

### Prueba 2: Consultar el Estado en Vivo de los 10 Sensores de la Planta
```bash
curl -s http://127.0.0.1/api/plant/status | python3 -m json.tool | head -n 30
```

### Prueba 3: Inyectar una Anomalía Crítica (`> 90 mm/s`) y Verificar Generación de Alerta
```bash
# Inyectar pico crítico de 96.4 mm/s en sensor_A (Nivel 1 - Turbina Principal A1)
curl -i -X POST http://127.0.0.1/api/simulation/inject \
  -H "Content-Type: application/json" \
  -d '{"sensorId":"sensor_A","vibrationValue":96.4,"burstCount":1}'

# Esperar 2 segundos y consultar las alertas pendientes en operator_console_backend
sleep 2
curl -s http://127.0.0.1/api/alerts/pending | python3 -m json.tool
```

### Prueba 4: Verificar Seguridad (`401 Unauthorized` sin credenciales) y Despacho Transaccional Autenticado
```bash
# 1. Intentar consultar la auditoría sin credenciales (Debe devolver HTTP 401)
curl -i http://127.0.0.1/api/dispatcher/api/v1/audit

# 2. Consultar la auditoría con credenciales válidas de operador (Devuelve HTTP 200 + JSON)
curl -s -u operator:operator123 http://127.0.0.1/api/dispatcher/api/v1/audit | python3 -m json.tool | head -n 30
```

### Prueba 5: Verificar Persistencia Directa en la Base de Datos PostgreSQL 16
```bash
sudo docker exec iot_postgres psql -U iot_admin -d mantenimiento_db \
  -c "SELECT id, alert_id, sensor_id, chosen_action, operator_id, status, routing_target, delay_ms, recorded_at FROM operator_decision_audit ORDER BY id DESC LIMIT 10;"
```

### Prueba 6: Disparar Boletín Corporativo ISO 10816-3 en Vivo hacia Amazon SNS
```bash
curl -s -X POST http://127.0.0.1/api/dispatcher/api/v1/notifications/test \
  -u operator:operator123 | python3 -m json.tool
```
* **Resultado esperado**: `"deliveryStatus": "AWS_SNS_OK (...)"` y recepción inmediata del correo corporativo en `alejandro.espaname@campusucc.edu.co`.
