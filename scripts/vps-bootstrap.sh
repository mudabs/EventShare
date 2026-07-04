#!/usr/bin/env bash
# Run ONCE on a fresh Ubuntu VPS as a sudo-capable user: installs Docker + Compose
# and opens the firewall. Usage:  bash vps-bootstrap.sh
set -euo pipefail

sudo apt-get update
sudo apt-get install -y ca-certificates curl git unzip ufw

# Docker Engine + Compose plugin (official repo)
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Let the current user run docker without sudo
sudo usermod -aG docker "$USER"

# Firewall: SSH + web
sudo ufw allow OpenSSH || true
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable

echo
echo "Docker installed. Log out and back in (or run: newgrp docker) so the docker group applies."
docker --version || true
