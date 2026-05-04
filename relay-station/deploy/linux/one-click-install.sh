#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-lan-tunnel-relay-station}"
RUN_USER="${RUN_USER:-lan-tunnel}"
APP_DIR="${APP_DIR:-/opt/lan-tunnel-relay-station}"
CONFIG_DIR="${CONFIG_DIR:-/etc/lan-tunnel-relay-station}"
SERVICE_FILE="${SERVICE_FILE:-/etc/systemd/system/${SERVICE_NAME}.service}"
SHORTCUT_NAME="${SHORTCUT_NAME:-bb-relay}"
SHORTCUT_FILE="${SHORTCUT_FILE:-/usr/local/bin/${SHORTCUT_NAME}}"

API_USERNAME="${API_USERNAME:-node-api}"
API_PORT="${API_PORT:-8080}"
CONTROL_PORT="${CONTROL_PORT:-25566}"
PUBLIC_MIN="${PUBLIC_MIN:-25565}"
PUBLIC_MAX="${PUBLIC_MAX:-25665}"
MAX_TUNNELS_PER_TOKEN="${MAX_TUNNELS_PER_TOKEN:-1}"
MAX_CONCURRENT_STREAMS_PER_TOKEN="${MAX_CONCURRENT_STREAMS_PER_TOKEN:-32}"
MAX_PENDING_CLIENTS_PER_TOKEN="${MAX_PENDING_CLIENTS_PER_TOKEN:-32}"
CONNECT_TIMEOUT_MILLIS="${CONNECT_TIMEOUT_MILLIS:-15000}"
PENDING_CLIENT_TIMEOUT_MILLIS="${PENDING_CLIENT_TIMEOUT_MILLIS:-15000}"

OVERWRITE_CONFIG="${OVERWRITE_CONFIG:-0}"
INSTALL_PACKAGES="${INSTALL_PACKAGES:-1}"
START_SERVICE="${START_SERVICE:-1}"
INSTALL_SHORTCUT="${INSTALL_SHORTCUT:-1}"
OPEN_UFW="${OPEN_UFW:-0}"
MANAGER_IP="${MANAGER_IP:-}"
PUBLIC_HOST="${PUBLIC_HOST:-}"
GO_INSTALL_VERSION="${GO_INSTALL_VERSION:-1.22.12}"
REPO_ARCHIVE_URL="${REPO_ARCHIVE_URL:-https://github.com/lxd2study/BlockBridge/archive/refs/heads/main.tar.gz}"
INSTALL_SCRIPT_URL="${INSTALL_SCRIPT_URL:-https://raw.githubusercontent.com/lxd2study/BlockBridge/main/relay-station/deploy/linux/one-click-install.sh}"
SOURCE_DIR="${SOURCE_DIR:-}"

if [ "$(id -u)" -ne 0 ]; then
  echo "请用 root 执行：sudo bash deploy/linux/one-click-install.sh"
  exit 1
fi

if [ "$INSTALL_PACKAGES" = "1" ] && command -v apt-get >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y ca-certificates curl openssl tar build-essential python3
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd || pwd)"
if [ -n "$SOURCE_DIR" ]; then
  PROJECT_DIR="$(cd "$SOURCE_DIR" && pwd)"
else
  PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." 2>/dev/null && pwd || pwd)"
fi

if [ ! -f "$PROJECT_DIR/go.mod" ] || [ ! -f "$PROJECT_DIR/main.go" ]; then
  if ! command -v curl >/dev/null 2>&1 || ! command -v tar >/dev/null 2>&1; then
    echo "没有找到 relay-station 源码目录，并且缺少 curl/tar，无法自动下载源码。"
    exit 1
  fi
  WORK_DIR="$(mktemp -d)"
  echo "没有找到本地源码，正在下载 BlockBridge 源码..."
  curl -fL "$REPO_ARCHIVE_URL" -o "$WORK_DIR/source.tar.gz"
  tar -xzf "$WORK_DIR/source.tar.gz" -C "$WORK_DIR"
  PROJECT_DIR="$(find "$WORK_DIR" -maxdepth 3 -type f -path "*/relay-station/go.mod" -print -quit | sed 's#/go.mod$##')"
  if [ -z "$PROJECT_DIR" ] || [ ! -f "$PROJECT_DIR/main.go" ]; then
    echo "源码包里没有找到 relay-station。"
    exit 1
  fi
fi

go_is_usable() {
  command -v go >/dev/null 2>&1 || return 1
  local version major minor
  version="$(go version | awk '{print $3}' | sed 's/^go//')"
  major="${version%%.*}"
  minor="${version#*.}"
  minor="${minor%%.*}"
  [ "${major:-0}" -gt 1 ] || { [ "${major:-0}" -eq 1 ] && [ "${minor:-0}" -ge 22 ]; }
}

install_go() {
  local arch tarball url
  arch="$(uname -m)"
  case "$arch" in
    x86_64|amd64) arch="amd64" ;;
    aarch64|arm64) arch="arm64" ;;
    *) echo "不支持的 CPU 架构：$arch"; exit 1 ;;
  esac
  tarball="/tmp/go${GO_INSTALL_VERSION}.linux-${arch}.tar.gz"
  url="https://go.dev/dl/go${GO_INSTALL_VERSION}.linux-${arch}.tar.gz"
  echo "安装 Go ${GO_INSTALL_VERSION} (${arch})..."
  curl -fL "$url" -o "$tarball"
  rm -rf /usr/local/go
  tar -C /usr/local -xzf "$tarball"
  rm -f "$tarball"
  export PATH="/usr/local/go/bin:$PATH"
}

if ! go_is_usable; then
  install_go
fi

if ! go_is_usable; then
  echo "Go 安装失败或版本仍低于 1.22。当前：$(go version 2>/dev/null || echo 'not found')"
  exit 1
fi

random_hex() {
  openssl rand -hex "$1"
}

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '%s' "$value"
}

API_PASSWORD="${API_PASSWORD:-$(random_hex 18)}"
DEFAULT_TOKEN="${DEFAULT_TOKEN:-$(random_hex 24)}"
TOKEN_NAME="${TOKEN_NAME:-default}"

cd "$PROJECT_DIR"
mkdir -p bin
go build -trimpath -ldflags "-s -w" -o bin/relay-station .

if ! id "$RUN_USER" >/dev/null 2>&1; then
  useradd --system --home-dir "$APP_DIR" --shell /usr/sbin/nologin "$RUN_USER"
fi

install -d -o "$RUN_USER" -g "$RUN_USER" "$APP_DIR"
install -d -m 750 -o "$RUN_USER" -g "$RUN_USER" "$CONFIG_DIR"
install -m 755 bin/relay-station "$APP_DIR/relay-station"
chown "$RUN_USER:$RUN_USER" "$APP_DIR/relay-station"

CONFIG_FILE="$CONFIG_DIR/station.json"
if [ ! -f "$CONFIG_FILE" ] || [ "$OVERWRITE_CONFIG" = "1" ]; then
  TMP_CONFIG="$(mktemp)"
  cat > "$TMP_CONFIG" <<JSON
{
  "relay": {
    "bind": "0.0.0.0",
    "controlPort": ${CONTROL_PORT},
    "publicMin": ${PUBLIC_MIN},
    "publicMax": ${PUBLIC_MAX},
    "connectTimeoutMillis": ${CONNECT_TIMEOUT_MILLIS},
    "pendingClientTimeoutMillis": ${PENDING_CLIENT_TIMEOUT_MILLIS},
    "maxTunnelsPerToken": ${MAX_TUNNELS_PER_TOKEN},
    "maxConcurrentStreamsPerToken": ${MAX_CONCURRENT_STREAMS_PER_TOKEN},
    "maxPendingClientsPerToken": ${MAX_PENDING_CLIENTS_PER_TOKEN}
  },
  "api": {
    "bind": "0.0.0.0:${API_PORT}",
    "username": "$(json_escape "$API_USERNAME")",
    "password": "$(json_escape "$API_PASSWORD")"
  },
  "tokens": [
    {
      "name": "$(json_escape "$TOKEN_NAME")",
      "token": "$(json_escape "$DEFAULT_TOKEN")",
      "enabled": true
    }
  ]
}
JSON
  install -m 640 -o "$RUN_USER" -g "$RUN_USER" "$TMP_CONFIG" "$CONFIG_FILE"
  rm -f "$TMP_CONFIG"
else
  echo "已存在 $CONFIG_FILE，保留原配置。设置 OVERWRITE_CONFIG=1 可覆盖生成。"
fi

if command -v python3 >/dev/null 2>&1; then
  CONFIG_VALUES="$(python3 - "$CONFIG_FILE" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as f:
    data = json.load(f)

api = data.get("api") or {}
relay = data.get("relay") or {}
tokens = data.get("tokens") or []
token = tokens[0] if tokens else {}

print(api.get("username", "node-api"))
print(api.get("password", "请查看配置文件"))
print(str(api.get("bind", "0.0.0.0:8080")).rsplit(":", 1)[-1])
print(str(relay.get("controlPort", 25566)))
print(str(relay.get("publicMin", 25565)))
print(str(relay.get("publicMax", 25665)))
print(token.get("token", "请查看配置文件"))
PY
)"
  API_USERNAME="$(printf '%s\n' "$CONFIG_VALUES" | sed -n '1p')"
  API_PASSWORD="$(printf '%s\n' "$CONFIG_VALUES" | sed -n '2p')"
  API_PORT="$(printf '%s\n' "$CONFIG_VALUES" | sed -n '3p')"
  CONTROL_PORT="$(printf '%s\n' "$CONFIG_VALUES" | sed -n '4p')"
  PUBLIC_MIN="$(printf '%s\n' "$CONFIG_VALUES" | sed -n '5p')"
  PUBLIC_MAX="$(printf '%s\n' "$CONFIG_VALUES" | sed -n '6p')"
  DEFAULT_TOKEN="$(printf '%s\n' "$CONFIG_VALUES" | sed -n '7p')"
elif [ -f "$CONFIG_FILE" ] && [ "$OVERWRITE_CONFIG" != "1" ]; then
  API_PASSWORD="请查看 ${CONFIG_FILE}"
  DEFAULT_TOKEN="请查看 ${CONFIG_FILE}"
fi

TMP_SERVICE="$(mktemp)"
cat > "$TMP_SERVICE" <<SERVICE
[Unit]
Description=LAN Tunnel Relay Station
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${RUN_USER}
Group=${RUN_USER}
WorkingDirectory=${APP_DIR}
ExecStart=${APP_DIR}/relay-station --config ${CONFIG_FILE}
Restart=always
RestartSec=3
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
SERVICE
install -m 644 "$TMP_SERVICE" "$SERVICE_FILE"
rm -f "$TMP_SERVICE"

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
if [ "$START_SERVICE" = "1" ]; then
  systemctl restart "$SERVICE_NAME"
fi

if [ "$INSTALL_SHORTCUT" = "1" ]; then
  TMP_SHORTCUT="$(mktemp)"
  SHORTCUT_BODY="$(cat <<'SHORTCUT'
#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="__SERVICE_NAME__"
RUN_USER="__RUN_USER__"
CONFIG_FILE="__CONFIG_FILE__"
CONFIG_DIR="__CONFIG_DIR__"
APP_DIR="__APP_DIR__"
SERVICE_FILE="__SERVICE_FILE__"
SHORTCUT_NAME="__SHORTCUT_NAME__"
SHORTCUT_FILE="__SHORTCUT_FILE__"
GO_INSTALL_VERSION="__GO_INSTALL_VERSION__"
REPO_ARCHIVE_URL="__REPO_ARCHIVE_URL__"
INSTALL_SCRIPT_URL="__INSTALL_SCRIPT_URL__"

need_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    echo "该操作需要 root 或 sudo：$*" >&2
    exit 1
  fi
}

print_help() {
  cat <<HELP
BlockBridge relay-station 快捷命令

用法：
  ${SHORTCUT_NAME} status        查看服务状态
  ${SHORTCUT_NAME} logs          实时查看日志
  ${SHORTCUT_NAME} tail [数量]   查看最近日志，默认 100 行
  ${SHORTCUT_NAME} restart       重启服务
  ${SHORTCUT_NAME} start         启动服务
  ${SHORTCUT_NAME} stop          停止服务
  ${SHORTCUT_NAME} config        编辑节点配置
  ${SHORTCUT_NAME} upgrade       拉取 main 最新版并升级，保留现有配置
  ${SHORTCUT_NAME} info          显示安装路径
  ${SHORTCUT_NAME} help          显示帮助
HELP
}

cmd="${1:-status}"
case "$cmd" in
  status)
    systemctl status "$SERVICE_NAME" --no-pager
    ;;
  logs|log)
    journalctl -u "$SERVICE_NAME" -f
    ;;
  tail)
    count="${2:-100}"
    case "$count" in
      ""|*[!0-9]*) echo "tail 数量必须是数字。" >&2; exit 1 ;;
    esac
    journalctl -u "$SERVICE_NAME" -n "$count" --no-pager
    ;;
  restart|start|stop)
    need_root systemctl "$cmd" "$SERVICE_NAME"
    ;;
  config)
    if command -v nano >/dev/null 2>&1; then
      need_root nano "$CONFIG_FILE"
    else
      need_root vi "$CONFIG_FILE"
    fi
    need_root systemctl restart "$SERVICE_NAME"
    ;;
  upgrade|update)
    if ! command -v curl >/dev/null 2>&1; then
      echo "缺少 curl，无法下载升级脚本。" >&2
      exit 1
    fi
    if [ -f "$CONFIG_FILE" ]; then
      backup="${CONFIG_FILE}.bak.$(date +%Y%m%d%H%M%S)"
      need_root cp "$CONFIG_FILE" "$backup"
      echo "已备份配置：$backup"
    fi
    if [ "$(id -u)" -eq 0 ]; then
      curl -fsSL "$INSTALL_SCRIPT_URL" | env SERVICE_NAME="$SERVICE_NAME" RUN_USER="$RUN_USER" APP_DIR="$APP_DIR" CONFIG_DIR="$CONFIG_DIR" SERVICE_FILE="$SERVICE_FILE" SHORTCUT_NAME="$SHORTCUT_NAME" SHORTCUT_FILE="$SHORTCUT_FILE" GO_INSTALL_VERSION="$GO_INSTALL_VERSION" REPO_ARCHIVE_URL="$REPO_ARCHIVE_URL" INSTALL_SCRIPT_URL="$INSTALL_SCRIPT_URL" bash
    else
      curl -fsSL "$INSTALL_SCRIPT_URL" | sudo env SERVICE_NAME="$SERVICE_NAME" RUN_USER="$RUN_USER" APP_DIR="$APP_DIR" CONFIG_DIR="$CONFIG_DIR" SERVICE_FILE="$SERVICE_FILE" SHORTCUT_NAME="$SHORTCUT_NAME" SHORTCUT_FILE="$SHORTCUT_FILE" GO_INSTALL_VERSION="$GO_INSTALL_VERSION" REPO_ARCHIVE_URL="$REPO_ARCHIVE_URL" INSTALL_SCRIPT_URL="$INSTALL_SCRIPT_URL" bash
    fi
    ;;
  info)
    cat <<INFO
服务名称：$SERVICE_NAME
程序路径：$APP_DIR/relay-station
配置文件：$CONFIG_FILE
快捷命令：$(command -v "$SHORTCUT_NAME" 2>/dev/null || echo "$SHORTCUT_NAME")
INFO
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    echo "未知命令：$cmd" >&2
    print_help
    exit 1
    ;;
esac
SHORTCUT
)"
  SHORTCUT_BODY="${SHORTCUT_BODY//__SERVICE_NAME__/$SERVICE_NAME}"
  SHORTCUT_BODY="${SHORTCUT_BODY//__RUN_USER__/$RUN_USER}"
  SHORTCUT_BODY="${SHORTCUT_BODY//__CONFIG_FILE__/$CONFIG_FILE}"
  SHORTCUT_BODY="${SHORTCUT_BODY//__CONFIG_DIR__/$CONFIG_DIR}"
  SHORTCUT_BODY="${SHORTCUT_BODY//__APP_DIR__/$APP_DIR}"
  SHORTCUT_BODY="${SHORTCUT_BODY//__SERVICE_FILE__/$SERVICE_FILE}"
  SHORTCUT_BODY="${SHORTCUT_BODY//__SHORTCUT_NAME__/$SHORTCUT_NAME}"
  SHORTCUT_BODY="${SHORTCUT_BODY//__SHORTCUT_FILE__/$SHORTCUT_FILE}"
  SHORTCUT_BODY="${SHORTCUT_BODY//__GO_INSTALL_VERSION__/$GO_INSTALL_VERSION}"
  SHORTCUT_BODY="${SHORTCUT_BODY//__REPO_ARCHIVE_URL__/$REPO_ARCHIVE_URL}"
  SHORTCUT_BODY="${SHORTCUT_BODY//__INSTALL_SCRIPT_URL__/$INSTALL_SCRIPT_URL}"
  printf '%s\n' "$SHORTCUT_BODY" > "$TMP_SHORTCUT"
  install -d "$(dirname "$SHORTCUT_FILE")"
  install -m 755 "$TMP_SHORTCUT" "$SHORTCUT_FILE"
  rm -f "$TMP_SHORTCUT"
fi

if [ "$OPEN_UFW" = "1" ] && command -v ufw >/dev/null 2>&1; then
  ufw allow "${CONTROL_PORT}/tcp"
  ufw allow "${PUBLIC_MIN}:${PUBLIC_MAX}/tcp"
  if [ -n "$MANAGER_IP" ]; then
    ufw allow from "$MANAGER_IP" to any port "$API_PORT" proto tcp
  else
    echo "未设置 MANAGER_IP，未自动开放节点 API 端口 ${API_PORT}。"
  fi
fi

if [ -z "$PUBLIC_HOST" ]; then
  PUBLIC_HOST="$(curl -fsS --max-time 4 https://api.ipify.org 2>/dev/null || true)"
fi
if [ -z "$PUBLIC_HOST" ]; then
  PUBLIC_HOST="$(hostname -I 2>/dev/null | awk '{print $1}')"
fi
if [ -z "$PUBLIC_HOST" ]; then
  PUBLIC_HOST="节点公网IP"
fi

if [ "$INSTALL_SHORTCUT" = "1" ]; then
  SHORTCUT_COMMANDS="$(cat <<EOF
  ${SHORTCUT_NAME} status
  ${SHORTCUT_NAME} logs
  ${SHORTCUT_NAME} upgrade
EOF
)"
else
  SHORTCUT_COMMANDS="  未安装快捷命令。设置 INSTALL_SHORTCUT=1 可启用。"
fi

cat <<EOF

relay-station 已安装完成。

服务命令：
  systemctl status ${SERVICE_NAME}
  journalctl -u ${SERVICE_NAME} -f
${SHORTCUT_COMMANDS}

relay-manager 添加节点时填写：
  公网 Host: ${PUBLIC_HOST}
  API 端点: http://${PUBLIC_HOST}:${API_PORT}
  API 用户: ${API_USERNAME}
  API 密码: ${API_PASSWORD}

Minecraft Mod 默认连接：
  中转服务器地址: ${PUBLIC_HOST}
  控制端口: ${CONTROL_PORT}
  访问令牌: ${DEFAULT_TOKEN}
  公网端口: 0 自动分配，或填写 ${PUBLIC_MIN}-${PUBLIC_MAX} 内未占用且非控制/API 的端口

需要放行端口：
  TCP ${CONTROL_PORT}
  TCP ${PUBLIC_MIN}-${PUBLIC_MAX}
  TCP ${API_PORT} 仅允许 relay-manager 服务器访问

配置文件：
  ${CONFIG_FILE}

EOF
