#!/usr/bin/env bash

set -e

echo "========================================================================"
echo "[INICIO] DESPLIEGUE EN INSTANCIA AWS EC2 (CAPA GRATUITA / UBUNTU SERVER)"
echo "========================================================================"

echo "[PASO 1] Actualizando repositorios del sistema..."
sudo apt-get update -y
sudo apt-get install -y curl wget git apt-transport-https ca-certificates gnupg lsb-release ufw

if [ ! -f /swapfile ]; then
    echo "[PASO 2] Configurando 6GB de memoria Swap en disco SSD EBS..."
    sudo fallocate -l 6G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=6144
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    sudo sysctl -w vm.swappiness=60
    echo "vm.swappiness=60" | sudo tee -a /etc/sysctl.conf
fi

echo "[PASO 3] Configurando limites del kernel (sysctl vm.max_map_count)..."
sudo sysctl -w vm.max_map_count=262144
grep -q "vm.max_map_count=262144" /etc/sysctl.conf || echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf

if ! command -v docker &> /dev/null; then
    echo "[PASO 4] Instalando Docker Engine oficial..."
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg

    echo \
      "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | \
      sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

    sudo apt-get update -y
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    sudo usermod -aG docker "$USER"
    sudo systemctl enable docker
    sudo systemctl start docker
    echo "[OK] Docker instalado exitosamente."
else
    echo "[OK] Docker ya se encuentra instalado."
fi

echo "[PASO 5] Configurando Firewall Perimetral (UFW)..."
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw limit 22/tcp comment 'SSH con Rate Limit'
sudo ufw allow 80/tcp comment 'Nginx Reverse Proxy HTTP'
sudo ufw allow 443/tcp comment 'Nginx Reverse Proxy HTTPS'
sudo ufw --force enable

echo "[PASO 6] Compilando y levantando los 15 contenedores con Docker Compose..."
sudo docker compose pull postgres zookeeper kafka || true
sudo docker compose up -d --build

if [ -f ./aws/hardening-ubuntu.sh ]; then
    echo "[PASO 7] Aplicando Hardening de Ubuntu Server e integraciones AWS..."
    chmod +x ./aws/hardening-ubuntu.sh
    ./aws/hardening-ubuntu.sh
fi

PUBLIC_IP=$(curl -s http://checkip.amazonaws.com || echo "77.113.90.86")

echo "========================================================================"
echo "[COMPLETADO] DESPLIEGUE EN AWS EC2 FINALIZADO CON EXITO"
echo "========================================================================"
echo "Acceso seguro a la plataforma (TLS 1.2 / 1.3):"
echo " - Consola SCADA y Gemelo 3D: https://scada-ucc-alejandro.${PUBLIC_IP}.sslip.io"
echo "========================================================================"
