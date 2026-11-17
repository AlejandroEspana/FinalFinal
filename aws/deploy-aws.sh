#!/usr/bin/env bash
# =============================================================================
# Script de Despliegue Automatizado en AWS EC2 (Optimizado para Free Tier)
# Plataforma de Mantenimiento Predictivo Interactivo (IoT Industrial)
# =============================================================================

set -e

echo "========================================================================"
echo "🚀 INICIANDO DESPLIEGUE EN INSTANCIA AWS EC2 (CAPA GRATUITA / UBUNTU)"
echo "========================================================================"

# 1. Actualización de paquetes del sistema operativo
echo "📦 Actualizando repositorios del sistema..."
sudo apt-get update -y
sudo apt-get install -y curl wget git apt-transport-https ca-certificates gnupg lsb-release ufw

# 2. Crear archivo Swap de 6GB (crítico para instancias Free Tier t2.micro / t3.micro de 1GB RAM)
if [ ! -f /swapfile ]; then
    echo "💾 Configurando 6GB de memoria Swap en disco SSD EBS..."
    sudo fallocate -l 6G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=6144
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    sudo sysctl -w vm.swappiness=60
    echo "vm.swappiness=60" | sudo tee -a /etc/sysctl.conf
fi

# 3. Configurar parámetros del kernel requeridos por Kafka y Docker
echo "⚙️ Configurando límites del kernel (sysctl vm.max_map_count)..."
sudo sysctl -w vm.max_map_count=262144
grep -q "vm.max_map_count=262144" /etc/sysctl.conf || echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf

# 4. Instalación de Docker y Docker Compose Plugin (Repositorio Oficial Docker)
if ! command -v docker &> /dev/null; then
    echo "🐳 Instalando Docker Engine oficial..."
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
    echo "✅ Docker instalado exitosamente."
else
    echo "✅ Docker ya se encuentra instalado."
fi

# 5. Configuración del Firewall UFW (puertos públicos necesarios; BD y brokers quedan en 127.0.0.1)
echo "🛡️ Configurando Firewall (UFW)..."
sudo ufw allow 22/tcp       comment 'SSH'
sudo ufw allow 3000/tcp     comment 'SCADA React+TS Frontend Nginx'
sudo ufw allow 8080/tcp     comment 'Action Dispatcher API + PostgreSQL Audit'
sudo ufw allow 8081/tcp     comment 'Plant Monitor WebSocket'
sudo ufw allow 8082/tcp     comment 'Operator Alerts WebSocket'
sudo ufw allow 8089/tcp     comment 'Sensor Producer Telemetry Injector'
sudo ufw allow 8091:8094/tcp comment 'Actuator & Maintenance Worker Replicas'
sudo ufw --force enable

# 6. Despliegue de los Contenedores con Docker Compose
echo "🚀 Levantando y compilando todos los microservicios con Docker Compose..."
sudo docker compose pull postgres zookeeper kafka || true
sudo docker compose build
sudo docker compose up -d

PUBLIC_IP=$(curl -s http://checkip.amazonaws.com || echo "<IP_PUBLICA_EC2>")

echo "========================================================================"
echo "✅ DESPLIEGUE EN AWS COMPLETADO CON ÉXITO"
echo "========================================================================"
echo "Acceso a la plataforma:"
echo " - SCADA Dashboard (React + TS): http://${PUBLIC_IP}:3000"
echo " - API REST Dispatcher:          http://${PUBLIC_IP}:8080/api/v1/audit"
echo " - WebSocket Monitor Planta:     ws://${PUBLIC_IP}:8081/ws-plant"
echo " - WebSocket Consola Alertas:    ws://${PUBLIC_IP}:8082/ws-alerts"
echo "========================================================================"
