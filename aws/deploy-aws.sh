#!/usr/bin/env bash
# =============================================================================
# Script de Despliegue Automatizado en AWS EC2 (Ubuntu 22.04 / 24.04 LTS)
# Plataforma de Mantenimiento Predictivo Interactivo (IoT Industrial)
# =============================================================================

set -e

echo "========================================================================"
echo "🚀 INICIANDO DESPLIEGUE EN INSTANCIA AWS EC2"
echo "========================================================================"

# 1. Actualización de paquetes del sistema operativo
echo "📦 Actualizando repositorios del sistema..."
sudo apt-get update -y
sudo apt-get upgrade -y
sudo apt-get install -y curl wget git apt-transport-https ca-certificates gnupg lsb-release ufw

# 2. Configurar parámetros del kernel requeridos por Kafka y Docker
echo "⚙️ Configurando límites del kernel (sysctl vm.max_map_count)..."
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf

# 3. Instalación de Docker y Docker Compose Plugin (Repositorio Oficial Docker)
if ! command -v docker &> /dev/null; then
    echo "🐳 Instalando Docker Engine oficial..."
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg

    echo \
      "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | \
      sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

    sudo apt-get update -y
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Añadir usuario actual al grupo docker
    sudo usermod -aG docker "$USER"
    sudo systemctl enable docker
    sudo systemctl start docker
    echo "✅ Docker instalado exitosamente."
else
    echo "✅ Docker ya se encuentra instalado."
fi

# 4. Configuración del Firewall UFW (abrir puertos de la plataforma)
echo "🛡️ Configurando Firewall (UFW)..."
sudo ufw allow 22/tcp       comment 'SSH'
sudo ufw allow 3000/tcp     comment 'SCADA Frontend Nginx'
sudo ufw allow 8080/tcp     comment 'Action Dispatcher API'
sudo ufw allow 8081/tcp     comment 'Plant Monitor WebSocket'
sudo ufw allow 8082/tcp     comment 'Operator Alerts WebSocket'
sudo ufw allow 15672/tcp    comment 'RabbitMQ Management UI'
sudo ufw --force enable

# 5. Despliegue de los Contenedores con Docker Compose
echo "🚀 Levantando y compilando todos los microservicios con Docker Compose..."
docker compose pull || true
docker compose build --parallel
docker compose up -d

echo "========================================================================"
echo "✅ DESPLIEGUE EN AWS COMPLETADO CON ÉXITO"
echo "========================================================================"
echo "Acceso a la plataforma (reemplace <IP_PUBLICA_EC2> con la IP de su instancia):"
echo " - SCADA Dashboard Frontend:   http://<IP_PUBLICA_EC2>:3000/dashboard.html"
echo " - Consola RabbitMQ:           http://<IP_PUBLICA_EC2>:15672 (guest / guest)"
echo " - API REST Dispatcher:        http://<IP_PUBLICA_EC2>:8080/api/v1/decide"
echo " - WebSocket Monitor Planta:   ws://<IP_PUBLICA_EC2>:8081/ws-plant"
echo " - WebSocket Consola Alertas:  ws://<IP_PUBLICA_EC2>:8082/ws-alerts"
echo "========================================================================"
