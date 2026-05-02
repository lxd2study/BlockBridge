#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/lan-tunnel-relay-station"
CONFIG_DIR="/etc/lan-tunnel-relay-station"
SERVICE_FILE="/etc/systemd/system/lan-tunnel-relay-station.service"

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root: sudo bash deploy/linux/install-systemd.sh"
  exit 1
fi

if [ ! -f "bin/relay-station" ]; then
  echo "Missing bin/relay-station. Build it first with: ./scripts/build.sh"
  exit 1
fi

if ! id lan-tunnel >/dev/null 2>&1; then
  useradd --system --home-dir "$APP_DIR" --shell /usr/sbin/nologin lan-tunnel
fi

install -d -o lan-tunnel -g lan-tunnel "$APP_DIR"
install -d -m 750 -o lan-tunnel -g lan-tunnel "$CONFIG_DIR"
install -m 755 bin/relay-station "$APP_DIR/relay-station"
chown lan-tunnel:lan-tunnel "$APP_DIR/relay-station"

if [ ! -f "$CONFIG_DIR/station.json" ]; then
  install -m 640 -o lan-tunnel -g lan-tunnel config/station.json "$CONFIG_DIR/station.json"
  echo "Edit $CONFIG_DIR/station.json before starting the service."
fi

install -m 644 deploy/linux/lan-tunnel-relay-station.service "$SERVICE_FILE"
systemctl daemon-reload
systemctl enable lan-tunnel-relay-station
echo "Installed. Start with: systemctl start lan-tunnel-relay-station"
