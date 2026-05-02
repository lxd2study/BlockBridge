#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/lan-tunnel-relay-manager"
CONFIG_DIR="/etc/lan-tunnel-relay-manager"
SERVICE_FILE="/etc/systemd/system/lan-tunnel-relay-manager.service"

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root: sudo bash deploy/linux/install-systemd.sh"
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js 20+ is required before installing the service."
  exit 127
fi

if [ ! -d "node_modules" ] || [ ! -f "public/index.html" ]; then
  echo "Missing build output. Run first: ./scripts/build.sh"
  exit 1
fi

if ! id lan-tunnel-manager >/dev/null 2>&1; then
  useradd --system --home-dir "$APP_DIR" --shell /usr/sbin/nologin lan-tunnel-manager
fi

install -d -o lan-tunnel-manager -g lan-tunnel-manager "$APP_DIR"
install -d -m 750 -o lan-tunnel-manager -g lan-tunnel-manager "$CONFIG_DIR"
install -d -m 750 -o lan-tunnel-manager -g lan-tunnel-manager "$APP_DIR/data"

cp -R server public node_modules package.json "$APP_DIR/"
chown -R lan-tunnel-manager:lan-tunnel-manager "$APP_DIR"

if [ ! -f "$CONFIG_DIR/manager.json" ]; then
  install -m 640 -o lan-tunnel-manager -g lan-tunnel-manager config/manager.json "$CONFIG_DIR/manager.json"
  echo "Edit $CONFIG_DIR/manager.json before starting the service."
fi

install -m 644 deploy/linux/lan-tunnel-relay-manager.service "$SERVICE_FILE"
systemctl daemon-reload
systemctl enable lan-tunnel-relay-manager
echo "Installed. Start with: systemctl start lan-tunnel-relay-manager"
