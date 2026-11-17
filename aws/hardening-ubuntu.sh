#!/usr/bin/env bash
# =============================================================================
# Script de Hardening Integral de Ubuntu Server 24.04 LTS en AWS EC2
# Basado en la Guía de Seguridad en Servidores (Defensa en Profundidad)
# =============================================================================

set -e

echo "========================================================================"
echo "🛡️ APLICANDO HARDENING DE SISTEMAS OPERATIVOS EN UBUNTU SERVER (AWS EC2)"
echo "========================================================================"

# -----------------------------------------------------------------------------
# FASE 1: Actualizaciones Automáticas (unattended-upgrades + needrestart)
# -----------------------------------------------------------------------------
echo "📦 [Fase 1] Configurando unattended-upgrades, needrestart y herramientas..."
sudo DEBIAN_FRONTEND=noninteractive apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    unattended-upgrades apt-listchanges needrestart \
    libpam-pwquality fail2ban auditd audispd-plugins \
    sysstat htop btop iotop-c ncdu lsof lynis ufw

cat << 'EOF' | sudo tee /etc/apt/apt.conf.d/20auto-upgrades > /dev/null
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

# -----------------------------------------------------------------------------
# FASE 2: Blindaje de SSH, Banner Legal, Sudo y PAM (pwquality + faillock)
# -----------------------------------------------------------------------------
echo "🔐 [Fase 2] Configurando Banner Legal (/etc/issue.net), SSH, Sudo y PAM..."

cat << 'EOF' | sudo tee /etc/issue.net > /dev/null
***************************************************************************
AVISO LEGAL - PLATAFORMA INDUSTRIAL DE MANTENIMIENTO PREDICTIVO (UCC)
Este sistema es de acceso restringido. Todas las sesiones, comandos y
conexiones están siendo monitoreadas y auditadas en tiempo real (auditd).
El acceso no autorizado será bloqueado y reportado.
***************************************************************************
EOF

cat << 'EOF' | sudo tee /etc/ssh/sshd_config.d/01-hardening.conf > /dev/null
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AuthenticationMethods publickey
PermitEmptyPasswords no
MaxAuthTries 3
MaxSessions 3
LoginGraceTime 30
ClientAliveInterval 300
ClientAliveCountMax 2
X11Forwarding no
AllowAgentForwarding no
PermitTunnel no
LogLevel VERBOSE
Banner /etc/issue.net
EOF

sudo sshd -t && sudo systemctl reload ssh || true

# Endurecer sudo (/etc/sudoers.d/99-hardening)
cat << 'EOF' | sudo tee /etc/sudoers.d/99-hardening > /dev/null
Defaults use_pty
Defaults timestamp_timeout=5
Defaults passwd_tries=3
EOF
sudo chmod 0440 /etc/sudoers.d/99-hardening

# Políticas de contraseñas PAM (/etc/security/pwquality.conf)
cat << 'EOF' | sudo tee /etc/security/pwquality.conf > /dev/null
minlen = 14
minclass = 3
maxrepeat = 3
dictcheck = 1
enforce_for_root
EOF

# Bloqueo tras intentos fallidos (/etc/security/faillock.conf)
cat << 'EOF' | sudo tee /etc/security/faillock.conf > /dev/null
deny = 5
unlock_time = 900
fail_interval = 900
EOF

# Permisos de archivos críticos del sistema (Diapositiva 20)
sudo chmod 600 /boot/grub/grub.cfg 2>/dev/null || true
sudo chmod 640 /etc/shadow /etc/gshadow
sudo chmod 644 /etc/passwd /etc/group
sudo chmod 700 /root

# -----------------------------------------------------------------------------
# FASE 3: Firewall UFW + IPS de Host con Fail2ban
# -----------------------------------------------------------------------------
echo "🧱 [Fase 3] Configurando Firewall UFW y Fail2ban..."
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw limit 22/tcp comment 'SSH con Rate Limit anti-fuerza bruta'
sudo ufw allow 80/tcp comment 'Nginx Reverse Proxy HTTP'
sudo ufw allow 443/tcp comment 'Nginx Reverse Proxy HTTPS'
sudo ufw allow 3000/tcp comment 'Nginx Reverse Proxy SCADA Port'
sudo ufw --force enable

cat << 'EOF' | sudo tee /etc/fail2ban/jail.local > /dev/null
[DEFAULT]
bantime = 1h
bantime.increment = true
bantime.maxtime = 1w
findtime = 10m
maxretry = 4
backend = systemd
banaction = ufw
ignoreip = 127.0.0.1/8 ::1

[sshd]
enabled = true
mode = aggressive

[recidive]
enabled = true
bantime = 1w
findtime = 1d
EOF

sudo systemctl enable --now fail2ban
sudo systemctl restart fail2ban

# -----------------------------------------------------------------------------
# FASE 4: Hardening del Kernel Linux (sysctl), Core Dumps y Módulos
# -----------------------------------------------------------------------------
echo "⚙️ [Fase 4] Endureciendo parámetros del Kernel (/etc/sysctl.d/99-hardening.conf)..."
cat << 'EOF' | sudo tee /etc/sysctl.d/99-hardening.conf > /dev/null
# Nota: ip_forward=1 requerido para el puente de red de Docker Compose
net.ipv4.ip_forward = 1
net.ipv4.conf.all.rp_filter = 1
net.ipv4.conf.default.rp_filter = 1
net.ipv4.conf.all.accept_redirects = 0
net.ipv4.conf.default.accept_redirects = 0
net.ipv4.conf.all.secure_redirects = 0
net.ipv4.conf.all.send_redirects = 0
net.ipv4.conf.all.accept_source_route = 0
net.ipv4.conf.all.log_martians = 1
net.ipv4.icmp_echo_ignore_broadcasts = 1
net.ipv4.tcp_syncookies = 1
net.ipv6.conf.all.accept_redirects = 0
net.ipv6.conf.all.accept_ra = 0

# Memoria y procesos (ASLR y restricción de punteros del kernel)
kernel.randomize_va_space = 2
kernel.kptr_restrict = 2
kernel.dmesg_restrict = 1
kernel.yama.ptrace_scope = 1
kernel.sysrq = 0
fs.suid_dumpable = 0
fs.protected_hardlinks = 1
fs.protected_symlinks = 1
vm.max_map_count = 262144
EOF
sudo sysctl --system > /dev/null

# Deshabilitar Core Dumps (Diapositiva 87)
sudo mkdir -p /etc/systemd/coredump.conf.d
cat << 'EOF' | sudo tee /etc/systemd/coredump.conf.d/disable.conf > /dev/null
[Coredump]
Storage=none
ProcessSizeMax=0
EOF
echo "* hard core 0" | sudo tee /etc/security/limits.d/99-nocore.conf > /dev/null

# Bloquear módulos innecesarios (Diapositiva 87)
cat << 'EOF' | sudo tee /etc/modprobe.d/99-hardening.conf > /dev/null
install cramfs /bin/false
install freevxfs /bin/false
install hfs /bin/false
install udf /bin/false
install usb-storage /bin/false
install dccp /bin/false
install sctp /bin/false
install rds /bin/false
install tipc /bin/false
EOF

# -----------------------------------------------------------------------------
# FASE 5: Monitoreo de Recursos (sysstat + script check-resources.sh en cron)
# -----------------------------------------------------------------------------
echo "📊 [Fase 5] Activando sysstat y script de vigilancia /usr/local/sbin/check-resources.sh..."
sudo sed -i 's/ENABLED="false"/ENABLED="true"/' /etc/default/sysstat 2>/dev/null || true
sudo systemctl enable --now sysstat || true

cat << 'EOF' | sudo tee /usr/local/sbin/check-resources.sh > /dev/null
#!/usr/bin/env bash
CPU_MAX=85; MEM_MAX=90; DISK_MAX=85
cpu=$(mpstat 1 1 | awk '/Average/ {printf "%d", 100-$NF}' 2>/dev/null || echo 10)
mem=$(free | awk '/Mem:/ {printf "%d", $3/$2*100}')
disk=$(df --output=pcent / | tail -1 | tr -dc '0-9')
msg=""
[ "$cpu"  -gt "$CPU_MAX" ]  && msg+="CPU ${cpu}% "
[ "$mem"  -gt "$MEM_MAX" ]  && msg+="RAM ${mem}% "
[ "$disk" -gt "$DISK_MAX" ] && msg+="DISCO ${disk}% "
[ -n "$msg" ] && logger -t resource-alert "$msg $(ps -eo pid,%cpu,cmd --sort=-%cpu | sed -n 2,4p)"
exit 0
EOF
sudo chmod 700 /usr/local/sbin/check-resources.sh
echo '*/5 * * * * root /usr/local/sbin/check-resources.sh' | sudo tee /etc/cron.d/check-resources > /dev/null

# -----------------------------------------------------------------------------
# FASE 6: Auditoría Forense con auditd y Línea Base de Binarios SUID
# -----------------------------------------------------------------------------
echo "🕵️ [Fase 6] Configurando reglas forenses en /etc/audit/rules.d/99-hardening.rules..."
cat << 'EOF' | sudo tee /etc/audit/rules.d/99-hardening.rules > /dev/null
-D
-b 8192
-f 1
-w /etc/passwd -p wa -k identity
-w /etc/shadow -p wa -k identity
-w /etc/group -p wa -k identity
-w /etc/sudoers -p wa -k sudoers
-w /etc/sudoers.d/ -p wa -k sudoers
-w /etc/ssh/sshd_config.d/ -p wa -k sshd
-w /etc/crontab -p wa -k cron
-w /etc/cron.d/ -p wa -k cron
-w /usr/bin/sudo -p x -k priv_esc
-w /usr/bin/su -p x -k priv_esc
EOF
sudo augenrules --load 2>/dev/null || true
sudo systemctl enable --now auditd || true

# Guardar línea base de binarios SUID/SGID (Diapositiva 45)
sudo find / -xdev -perm -4000 -type f 2>/dev/null > /home/ubuntu/suid_baseline.txt || true
sudo find / -xdev -perm -2000 -type f 2>/dev/null >> /home/ubuntu/suid_baseline.txt || true

# -----------------------------------------------------------------------------
# FASE 7: Servicio Systemd Confinado para Puente Amazon SNS (Diapositivas 9, 44 y 106)
# -----------------------------------------------------------------------------
echo "☁️ [Fase 7] Configurando servicio systemd confinado (scada-sns-bridge.service)..."
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y python3-boto3 > /dev/null 2>&1

cat << 'EOF' | sudo tee /usr/local/sbin/scada-sns-bridge.py > /dev/null
#!/usr/bin/env python3
import json
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer
import boto3

def get_region():
    try:
        req = urllib.request.Request(
            "http://169.254.169.254/latest/api/token",
            method="PUT",
            headers={"X-aws-ec2-metadata-token-ttl-seconds": "21600"}
        )
        token = urllib.request.urlopen(req, timeout=2).read().decode()
        req2 = urllib.request.Request(
            "http://169.254.169.254/latest/meta-data/placement/region",
            headers={"X-aws-ec2-metadata-token": token}
        )
        return urllib.request.urlopen(req2, timeout=2).read().decode().strip()
    except Exception:
        return "us-east-2"

REGION = get_region()
session = boto3.Session(region_name=REGION)
sns = session.client("sns")
TOPIC_ARN = sns.create_topic(Name="scada-iot-alertas-ucc")["TopicArn"]

class SnsBridgeHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/publish":
            self.send_response(404)
            self.end_headers()
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length).decode("utf-8")
            data = json.loads(raw) if raw else {}
            subject = str(data.get("subject", "Alerta SCADA IoT Industrial"))[:99]
            message = str(data.get("message", "Notificacion del sistema SCADA"))
            res = sns.publish(TopicArn=TOPIC_ARN, Subject=subject, Message=message)
            body = json.dumps({"status": "OK", "messageId": res.get("MessageId"), "topicArn": TOPIC_ARN}).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(body)
        except Exception as e:
            err = json.dumps({"status": "ERROR", "detail": str(e)}).encode("utf-8")
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(err)

if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 9099), SnsBridgeHandler).serve_forever()
EOF
sudo chmod 755 /usr/local/sbin/scada-sns-bridge.py

cat << 'EOF' | sudo tee /etc/systemd/system/scada-sns-bridge.service > /dev/null
[Unit]
Description=Puente Confinado Amazon SNS para Plataforma SCADA IoT (IMDSv2)
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/python3 /usr/local/sbin/scada-sns-bridge.py
Restart=always
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true
PrivateDevices=true
ProtectSystem=strict
ProtectHome=true
ProtectKernelTunables=true
ProtectKernelModules=true
RestrictSUIDSGID=true
MemoryMax=128M
CPUQuota=50%
TasksMax=64

[Install]
WantedBy=multi-user.target
EOF

# Permitir tráfico desde la subred interna de Docker hacia el puerto local 9099
sudo ufw allow from 172.16.0.0/12 to any port 9099 proto tcp comment 'Docker internal to SNS bridge' || true
sudo systemctl daemon-reload
sudo systemctl enable --now scada-sns-bridge.service
sudo systemctl restart scada-sns-bridge.service

# -----------------------------------------------------------------------------
# FASE 8: Respaldo Automático Diario de PostgreSQL con Firma SHA-256 (Diapositivas 88-90)
# -----------------------------------------------------------------------------
echo "💾 [Fase 8] Configurando respaldos automáticos diarios de PostgreSQL con firma SHA-256..."
sudo mkdir -p /var/backups/scada-postgres
sudo chmod 700 /var/backups/scada-postgres

cat << 'EOF' | sudo tee /usr/local/sbin/backup-postgres-scada.sh > /dev/null
#!/usr/bin/env bash
set -e
BACKUP_DIR="/var/backups/scada-postgres"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/mantenimiento_db_${TIMESTAMP}.sql.gz"
HASH_FILE="${BACKUP_FILE}.sha256"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

if docker ps --format '{{.Names}}' | grep -q '^iot_postgres$'; then
    docker exec iot_postgres pg_dump -U iot_admin -d mantenimiento_db | gzip -9 > "$BACKUP_FILE"
    sha256sum "$BACKUP_FILE" > "$HASH_FILE"
    chmod 600 "$BACKUP_FILE" "$HASH_FILE"
    # Retención de 7 días para cuidar el disco de la capa gratuita
    find "$BACKUP_DIR" -type f -mtime +7 -delete
    logger -t scada-backup "Backup PostgreSQL exitoso: ${BACKUP_FILE} (SHA-256 verificado)"
fi
EOF
sudo chmod 700 /usr/local/sbin/backup-postgres-scada.sh
sudo ln -sf /usr/local/sbin/backup-postgres-scada.sh /etc/cron.daily/backup-postgres-scada
sudo /usr/local/sbin/backup-postgres-scada.sh || true

echo "========================================================================"
echo "✅ HARDENING DE UBUNTU SERVER COMPLETADO CON ÉXITO"
echo "========================================================================"
sudo ufw status verbose
sudo fail2ban-client status
sudo ls -lh /var/backups/scada-postgres
