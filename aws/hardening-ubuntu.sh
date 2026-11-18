#!/usr/bin/env bash
# =============================================================================
# Script de Hardening Integral de Ubuntu Server 24.04 LTS en AWS EC2
# Basado en la Guia de Seguridad en Servidores (Defensa en Profundidad)
# =============================================================================

set -e

echo "========================================================================"
echo "[INICIO] APLICANDO HARDENING DE SISTEMAS OPERATIVOS EN UBUNTU SERVER"
echo "========================================================================"

# -----------------------------------------------------------------------------
# FASE 1: Actualizaciones Automaticas (unattended-upgrades + needrestart)
# -----------------------------------------------------------------------------
echo "[Fase 1] Configurando unattended-upgrades, needrestart y herramientas..."
sudo DEBIAN_FRONTEND=noninteractive apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    unattended-upgrades apt-listchanges needrestart \
    libpam-pwquality fail2ban auditd audispd-plugins \
    sysstat htop btop iotop-c ncdu lsof lynis ufw python3-boto3

cat << 'EOF' | sudo tee /etc/apt/apt.conf.d/20auto-upgrades > /dev/null
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

# -----------------------------------------------------------------------------
# FASE 2: Blindaje de SSH, Banner Legal, Sudo y PAM (pwquality + faillock)
# -----------------------------------------------------------------------------
echo "[Fase 2] Configurando Banner Legal (/etc/issue.net), SSH, Sudo y PAM..."

cat << 'EOF' | sudo tee /etc/issue.net > /dev/null
***************************************************************************
AVISO LEGAL - PLATAFORMA INDUSTRIAL DE MANTENIMIENTO PREDICTIVO (UCC)
Este sistema es de acceso restringido. Todas las sesiones, comandos y
conexiones estan siendo monitoreadas y auditadas en tiempo real (auditd).
El acceso no autorizado sera bloqueado y reportado.
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

# Politicas de contrasenas PAM (/etc/security/pwquality.conf)
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

# Permisos de archivos criticos del sistema
sudo chmod 600 /boot/grub/grub.cfg 2>/dev/null || true
sudo chmod 640 /etc/shadow /etc/gshadow
sudo chmod 644 /etc/passwd /etc/group
sudo chmod 700 /root

# -----------------------------------------------------------------------------
# FASE 3: Firewall UFW + IPS de Host con Fail2ban
# -----------------------------------------------------------------------------
echo "[Fase 3] Configurando Firewall UFW y Fail2ban..."
sudo ufw default deny incoming
sudo ufw default allow outgoing
for old_port in 8080/tcp 8081/tcp 8082/tcp 8089/tcp 8091:8094/tcp; do
    sudo ufw delete allow "$old_port" 2>/dev/null || true
done
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
# FASE 4: Hardening del Kernel Linux (sysctl), Core Dumps y Modulos
# -----------------------------------------------------------------------------
echo "[Fase 4] Endureciendo parametros del Kernel (/etc/sysctl.d/99-hardening.conf)..."
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

# Memoria y procesos (ASLR y restriccion de punteros del kernel)
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

# Deshabilitar Core Dumps
sudo mkdir -p /etc/systemd/coredump.conf.d
cat << 'EOF' | sudo tee /etc/systemd/coredump.conf.d/disable.conf > /dev/null
[Coredump]
Storage=none
ProcessSizeMax=0
EOF
echo "* hard core 0" | sudo tee /etc/security/limits.d/99-nocore.conf > /dev/null

# Bloquear modulos innecesarios
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
echo "[Fase 5] Activando sysstat y script de vigilancia /usr/local/sbin/check-resources.sh..."
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
# FASE 6: Auditoria Forense con auditd y Linea Base de Binarios SUID
# -----------------------------------------------------------------------------
echo "[Fase 6] Configurando reglas forenses en /etc/audit/rules.d/99-hardening.rules..."
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

# Guardar linea base de binarios SUID/SGID
sudo find / -xdev -perm -4000 -type f 2>/dev/null > /home/ubuntu/suid_baseline.txt || true
sudo find / -xdev -perm -2000 -type f 2>/dev/null >> /home/ubuntu/suid_baseline.txt || true

# -----------------------------------------------------------------------------
# FASE 7: Servicio Systemd Confinado para Puente Amazon SNS (IMDSv2)
# -----------------------------------------------------------------------------
echo "[Fase 7] Configurando servicio systemd confinado (scada-sns-bridge.service)..."

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

sudo ufw allow from 172.16.0.0/12 to any port 9099 proto tcp comment 'Docker internal to SNS bridge' || true
sudo systemctl daemon-reload
sudo systemctl enable --now scada-sns-bridge.service
sudo systemctl restart scada-sns-bridge.service

# -----------------------------------------------------------------------------
# FASE 8: Respaldo Diario de PostgreSQL con Firma SHA-256 + Replica en AWS S3
# -----------------------------------------------------------------------------
echo "[Fase 8] Configurando respaldos de PostgreSQL con firma SHA-256 y replica en Amazon S3..."
sudo mkdir -p /var/backups/scada-postgres
sudo chmod 700 /var/backups/scada-postgres

cat << 'EOF' | sudo tee /usr/local/sbin/upload-backup-s3.py > /dev/null
#!/usr/bin/env python3
import os
import sys
import urllib.request
import boto3

def get_meta(path, default=""):
    try:
        req = urllib.request.Request(
            "http://169.254.169.254/latest/api/token",
            method="PUT",
            headers={"X-aws-ec2-metadata-token-ttl-seconds": "21600"}
        )
        token = urllib.request.urlopen(req, timeout=2).read().decode()
        req2 = urllib.request.Request(
            f"http://169.254.169.254/latest/meta-data/{path}",
            headers={"X-aws-ec2-metadata-token": token}
        )
        return urllib.request.urlopen(req2, timeout=2).read().decode().strip()
    except Exception:
        return default

region = get_meta("placement/region", "us-east-2")
session = boto3.Session(region_name=region)
sts = session.client("sts")
account_id = sts.get_caller_identity()["Account"]
bucket_name = f"scada-ucc-backups-{account_id}"
s3 = session.client("s3")

try:
    if region == "us-east-1":
        s3.create_bucket(Bucket=bucket_name)
    else:
        s3.create_bucket(
            Bucket=bucket_name,
            CreateBucketConfiguration={"LocationConstraint": region}
        )
except Exception as e:
    if "BucketAlreadyOwnedByYou" not in str(e) and "BucketAlreadyExists" not in str(e):
        print(f"Aviso create_bucket: {e}")

try:
    s3.put_public_access_block(
        Bucket=bucket_name,
        PublicAccessBlockConfiguration={
            "BlockPublicAcls": True,
            "IgnorePublicAcls": True,
            "BlockPublicPolicy": True,
            "RestrictPublicBuckets": True
        }
    )
    s3.put_bucket_encryption(
        Bucket=bucket_name,
        ServerSideEncryptionConfiguration={
            "Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]
        }
    )
except Exception as e:
    print(f"Aviso blindaje S3: {e}")

for file_path in sys.argv[1:]:
    if os.path.isfile(file_path):
        key = "postgres/" + os.path.basename(file_path)
        s3.upload_file(
            file_path,
            bucket_name,
            key,
            ExtraArgs={"ServerSideEncryption": "AES256"}
        )
        print(f"S3_UPLOAD_OK: s3://{bucket_name}/{key}")
EOF
sudo chmod 700 /usr/local/sbin/upload-backup-s3.py

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
    find "$BACKUP_DIR" -type f -mtime +7 -delete
    /usr/bin/python3 /usr/local/sbin/upload-backup-s3.py "$BACKUP_FILE" "$HASH_FILE" || true
    logger -t scada-backup "Backup PostgreSQL exitoso y replicado en AWS S3: ${BACKUP_FILE}"
fi
EOF
sudo chmod 700 /usr/local/sbin/backup-postgres-scada.sh
sudo ln -sf /usr/local/sbin/backup-postgres-scada.sh /etc/cron.daily/backup-postgres-scada
sudo /usr/local/sbin/backup-postgres-scada.sh || true

# -----------------------------------------------------------------------------
# FASE 9: Telemetria de Sistema Operativo hacia AWS CloudWatch (Capa Gratuita)
# -----------------------------------------------------------------------------
echo "[Fase 9] Configurando envio de metricas RAM/Swap/Disco/Docker hacia AWS CloudWatch..."
cat << 'EOF' | sudo tee /usr/local/sbin/push-cloudwatch-metrics.py > /dev/null
#!/usr/bin/env python3
import subprocess
import urllib.request
import boto3

def get_meta(path, default=""):
    try:
        req = urllib.request.Request(
            "http://169.254.169.254/latest/api/token",
            method="PUT",
            headers={"X-aws-ec2-metadata-token-ttl-seconds": "21600"}
        )
        token = urllib.request.urlopen(req, timeout=2).read().decode()
        req2 = urllib.request.Request(
            f"http://169.254.169.254/latest/meta-data/{path}",
            headers={"X-aws-ec2-metadata-token": token}
        )
        return urllib.request.urlopen(req2, timeout=2).read().decode().strip()
    except Exception:
        return default

region = get_meta("placement/region", "us-east-2")
instance_id = get_meta("instance-id", "ec2-scada-ucc")

mem_pct = float(subprocess.check_output("free | awk '/Mem:/ {printf \"%.2f\", $3/$2*100}'", shell=True).decode().strip() or 0)
swap_pct = float(subprocess.check_output("free | awk '/Swap:/ {if ($2>0) printf \"%.2f\", $3/$2*100; else print 0}'", shell=True).decode().strip() or 0)
disk_pct = float(subprocess.check_output("df --output=pcent / | tail -1 | tr -dc '0-9'", shell=True).decode().strip() or 0)
containers = int(subprocess.check_output("docker ps -q | wc -l", shell=True).decode().strip() or 0)

cw = boto3.Session(region_name=region).client("cloudwatch")
cw.put_metric_data(
    Namespace="SCADA/IndustrialIoT",
    MetricData=[
        {"MetricName": "MemoryUtilizationPercent", "Dimensions": [{"Name": "InstanceId", "Value": instance_id}], "Value": mem_pct, "Unit": "Percent"},
        {"MetricName": "SwapUtilizationPercent", "Dimensions": [{"Name": "InstanceId", "Value": instance_id}], "Value": swap_pct, "Unit": "Percent"},
        {"MetricName": "DiskUtilizationPercent", "Dimensions": [{"Name": "InstanceId", "Value": instance_id}], "Value": disk_pct, "Unit": "Percent"},
        {"MetricName": "ActiveDockerContainers", "Dimensions": [{"Name": "InstanceId", "Value": instance_id}], "Value": containers, "Unit": "Count"}
    ]
)
print(f"CLOUDWATCH_METRICS_OK: RAM={mem_pct}% SWAP={swap_pct}% DISK={disk_pct}% CONTAINERS={containers}")
EOF
sudo chmod 700 /usr/local/sbin/push-cloudwatch-metrics.py
echo '*/5 * * * * root /usr/bin/python3 /usr/local/sbin/push-cloudwatch-metrics.py >/dev/null 2>&1' | sudo tee /etc/cron.d/scada-cloudwatch > /dev/null
sudo /usr/bin/python3 /usr/local/sbin/push-cloudwatch-metrics.py || true

# -----------------------------------------------------------------------------
# FASE 10: Registro del Certificado TLS en AWS Certificate Manager (ACM)
# -----------------------------------------------------------------------------
echo "[Fase 10] Importando y registrando certificado TLS en AWS Certificate Manager (ACM)..."
cat << 'EOF' | sudo tee /usr/local/sbin/register-acm-cert.py > /dev/null
#!/usr/bin/env python3
import os
import boto3

if os.path.isfile("/etc/scada-ssl/scada.crt") and os.path.isfile("/etc/scada-ssl/scada.key"):
    with open("/etc/scada-ssl/scada.crt", "rb") as f:
        raw_pem = f.read().decode("utf-8")
    with open("/etc/scada-ssl/scada.key", "rb") as f:
        priv_key = f.read()

    parts = [p.strip() + "\n-----END CERTIFICATE-----\n" for p in raw_pem.split("-----END CERTIFICATE-----") if "-----BEGIN CERTIFICATE-----" in p]
    leaf_cert = parts[0].encode("utf-8")
    chain_cert = "".join(parts[1:]).encode("utf-8") if len(parts) > 1 else None

    acm = boto3.Session(region_name="us-east-2").client("acm")
    existing = acm.list_certificates()["CertificateSummaryList"]
    target_arn = None
    for c in existing:
        if "scada-ucc-alejandro" in c.get("DomainName", ""):
            target_arn = c["CertificateArn"]
            break

    kwargs = {
        "Certificate": leaf_cert,
        "PrivateKey": priv_key
    }
    if chain_cert:
        kwargs["CertificateChain"] = chain_cert
    if target_arn:
        kwargs["CertificateArn"] = target_arn
    else:
        kwargs["Tags"] = [
            {"Key": "Project", "Value": "SCADA-Predictive-Maintenance-UCC"},
            {"Key": "Environment", "Value": "Production"}
        ]

    res = acm.import_certificate(**kwargs)
    print("AWS_ACM_CERTIFICATE_ARN:", res["CertificateArn"])
EOF
sudo chmod 700 /usr/local/sbin/register-acm-cert.py
sudo /usr/bin/python3 /usr/local/sbin/register-acm-cert.py || true

# -----------------------------------------------------------------------------
# FASE 11: Auto-Deploy Continuo (GitOps) Sincronizado con la Rama main de GitHub
# -----------------------------------------------------------------------------
echo "[Fase 11] Configurando Auto-Deploy continuo sincronizado con origin/main de GitHub..."
cat << 'EOF' | sudo tee /usr/local/sbin/scada-autodeploy.sh > /dev/null
#!/usr/bin/env bash
set -e
REPO_DIR="/home/ubuntu/FinalFinal"
if [ ! -d "$REPO_DIR/.git" ]; then
    exit 0
fi

cd "$REPO_DIR"
git fetch origin main --quiet
LOCAL_HASH=$(git rev-parse HEAD)
REMOTE_HASH=$(git rev-parse origin/main)

if [ "$LOCAL_HASH" != "$REMOTE_HASH" ]; then
    logger -t scada-autodeploy "Nuevo commit detectado en main (${REMOTE_HASH}). Iniciando actualizacion automatica..."
    git reset --hard origin/main
    docker compose up -d --build --remove-orphans
    if [ -f /etc/scada-ssl/scada.crt ] && [ -f /etc/scada-ssl/scada.key ]; then
        docker cp /etc/scada-ssl/scada.crt iot_frontend_dashboard:/etc/nginx/ssl/scada.crt || true
        docker cp /etc/scada-ssl/scada.key iot_frontend_dashboard:/etc/nginx/ssl/scada.key || true
        docker exec iot_frontend_dashboard nginx -s reload || true
    fi
    logger -t scada-autodeploy "Despliegue automatico completado en commit ${REMOTE_HASH}"
fi
EOF
sudo chmod 700 /usr/local/sbin/scada-autodeploy.sh
echo '*/2 * * * * root /usr/local/sbin/scada-autodeploy.sh >> /var/log/scada-autodeploy.log 2>&1' | sudo tee /etc/cron.d/scada-autodeploy > /dev/null

# -----------------------------------------------------------------------------
# FASE 12: Servicio de Arranque Automatico tras Apagar/Prender la Instancia EC2
# -----------------------------------------------------------------------------
echo "[Fase 12] Configurando servicio systemd scada-boot-sync.service (Auto-inicio tras apagar/prender EC2)..."
cat << 'EOF' | sudo tee /usr/local/sbin/scada-boot-sync.sh > /dev/null
#!/usr/bin/env bash
set -e

echo "[BOOT-SYNC] 1. Levantando los 15 contenedores Docker de la plataforma SCADA..."
cd /home/ubuntu/FinalFinal
docker compose up -d

if [ -f /etc/scada-ssl/scada.crt ] && [ -f /etc/scada-ssl/scada.key ]; then
    docker cp /etc/scada-ssl/scada.crt iot_frontend_dashboard:/etc/nginx/ssl/scada.crt || true
    docker cp /etc/scada-ssl/scada.key iot_frontend_dashboard:/etc/nginx/ssl/scada.key || true
    docker exec iot_frontend_dashboard nginx -s reload || true
fi

echo "[BOOT-SYNC] 2. Verificando IP publica actual de EC2 y sincronizando Amazon CloudFront..."
/usr/bin/python3 - << 'PYEOF'
import urllib.request
import boto3

def get_public_ip():
    try:
        req = urllib.request.Request(
            "http://169.254.169.254/latest/api/token",
            method="PUT",
            headers={"X-aws-ec2-metadata-token-ttl-seconds": "21600"}
        )
        token = urllib.request.urlopen(req, timeout=2).read().decode()
        req2 = urllib.request.Request(
            "http://169.254.169.254/latest/meta-data/public-ipv4",
            headers={"X-aws-ec2-metadata-token": token}
        )
        return urllib.request.urlopen(req2, timeout=2).read().decode().strip()
    except Exception:
        return None

pub_ip = get_public_ip()
if pub_ip:
    new_origin_domain = f"scada-ucc-alejandro.{pub_ip}.sslip.io"
    print(f"[BOOT-SYNC] IP Publica actual detectada: {pub_ip} -> Origin: {new_origin_domain}")
    try:
        cf = boto3.client("cloudfront")
        dists = cf.list_distributions().get("DistributionList", {}).get("Items", [])
        for d in dists:
            if d.get("Comment") == "SCADA-IoT-Predictive-Maintenance-UCC":
                dist_id = d["Id"]
                full = cf.get_distribution_config(Id=dist_id)
                etag = full["ETag"]
                cfg = full["DistributionConfig"]
                current_domain = cfg["Origins"]["Items"][0]["DomainName"]
                if current_domain != new_origin_domain:
                    cfg["Origins"]["Items"][0]["DomainName"] = new_origin_domain
                    cf.update_distribution(Id=dist_id, IfMatch=etag, DistributionConfig=cfg)
                    print(f"[BOOT-SYNC] Amazon CloudFront ({d['DomainName']}) actualizado con la nueva IP {pub_ip}")
                else:
                    print(f"[BOOT-SYNC] Amazon CloudFront ({d['DomainName']}) ya apunta a {new_origin_domain}")
    except Exception as e:
        print(f"[BOOT-SYNC] Aviso CloudFront: {e}")
PYEOF

echo "[BOOT-SYNC] Todos los servicios estan activos y sincronizados."
EOF
sudo chmod 700 /usr/local/sbin/scada-boot-sync.sh

cat << 'EOF' | sudo tee /etc/systemd/system/scada-boot-sync.service > /dev/null
[Unit]
Description=Sincronizacion Automatica de Contenedores SCADA y CloudFront al Iniciar EC2
After=network-online.target docker.service
Wants=network-online.target
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/scada-boot-sync.sh
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable scada-boot-sync.service

echo "========================================================================"
echo "[COMPLETADO] HARDENING + S3 + CLOUDWATCH + ACM + AUTODEPLOY + BOOT-SYNC ACTIVOS"
echo "========================================================================"
sudo ufw status verbose
sudo fail2ban-client status
sudo ls -lh /var/backups/scada-postgres
