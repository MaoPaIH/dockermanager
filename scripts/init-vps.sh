#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="sb-panel"
SERVICE_NAME="${SERVICE_NAME:-sb-panel}"
APP_DIR="${APP_DIR:-/opt/sb-panel}"
JAR_PATH="${JAR_PATH:-$APP_DIR/sb-panel.jar}"
ENV_FILE="${ENV_FILE:-$APP_DIR/panel.env}"
JAVA_PACKAGE="${JAVA_PACKAGE:-openjdk-17-jdk-headless}"

PANEL_JAR_URL="${PANEL_JAR_URL:-}"
PANEL_PORT="${PANEL_PORT:-18080}"
PANEL_ADMIN_USERNAME="${PANEL_ADMIN_USERNAME:-admin}"
PANEL_ADMIN_PASSWORD="${PANEL_ADMIN_PASSWORD:-}"
PANEL_DB_PATH="${PANEL_DB_PATH:-$APP_DIR/sb-panel.db}"
PANEL_LOG_FILE="${PANEL_LOG_FILE:-$APP_DIR/logs/sb-panel.log}"
PANEL_DOCKER_HOST="${PANEL_DOCKER_HOST:-unix:///var/run/docker.sock}"
PANEL_MONITOR_TICK_MS="${PANEL_MONITOR_TICK_MS:-10000}"
PUBLIC_IP="${PUBLIC_IP:-}"
PANEL_PUBLIC_BASE_URL="${PANEL_PUBLIC_BASE_URL:-}"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"

log() {
  printf '[%s] %s\n' "$(date '+%F %T')" "$*"
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_root() {
  if [ "${EUID:-$(id -u)}" -ne 0 ]; then
    die "Please run as root."
  fi
}

require_supported_os() {
  if [ ! -f /etc/os-release ]; then
    die "Only Debian/Ubuntu-like systems are supported."
  fi
  . /etc/os-release
  case "${ID:-}" in
    debian|ubuntu) ;;
    *)
      case "${ID_LIKE:-}" in
        *debian*|*ubuntu*) ;;
        *) die "Unsupported OS: ${PRETTY_NAME:-unknown}" ;;
      esac
      ;;
  esac
}

random_password() {
  local password=""
  while [ "${#password}" -lt 24 ]; do
    password="${password}$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"
  done
  printf '%s' "${password:0:24}"
}

detect_public_ip() {
  curl -fsS --max-time 8 https://api.ipify.org 2>/dev/null \
    || curl -fsS --max-time 8 https://ifconfig.me 2>/dev/null \
    || hostname -I 2>/dev/null | awk '{print $1}'
}

quote_env() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '"%s"' "$value"
}

write_env_line() {
  printf '%s=%s\n' "$1" "$(quote_env "$2")"
}

install_packages() {
  log "Installing Docker, JDK and base tools..."
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y ca-certificates curl gnupg openssl docker.io "$JAVA_PACKAGE"
}

enable_bbr() {
  log "Enabling BBR..."
  cat >/etc/sysctl.d/99-sb-panel-bbr.conf <<'EOF'
net.core.default_qdisc=fq
net.ipv4.tcp_congestion_control=bbr
EOF
  sysctl --system >/dev/null || true
  log "TCP congestion control: $(sysctl -n net.ipv4.tcp_congestion_control 2>/dev/null || echo unknown)"
}

configure_docker() {
  log "Configuring Docker daemon..."
  mkdir -p /etc/docker
  if [ -f /etc/docker/daemon.json ]; then
    cp -a /etc/docker/daemon.json "/etc/docker/daemon.json.bak.$(date +%Y%m%d%H%M%S)"
  fi

  # userland-proxy=false avoids one docker-proxy process per published port.
  cat >/etc/docker/daemon.json <<'EOF'
{
  "userland-proxy": false,
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "20m",
    "max-file": "3"
  }
}
EOF

  systemctl enable --now docker
  systemctl restart docker
  docker info >/dev/null
}

allow_panel_port() {
  if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -qi '^Status: active'; then
    log "Allowing panel port in ufw: $PANEL_PORT/tcp"
    ufw allow "${PANEL_PORT}/tcp"
  fi

  if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
    log "Allowing panel port in firewalld: $PANEL_PORT/tcp"
    firewall-cmd --permanent --add-port="${PANEL_PORT}/tcp"
    firewall-cmd --reload
  fi
}

download_panel_jar() {
  [ -n "$PANEL_JAR_URL" ] || die "PANEL_JAR_URL is required. Use a GitHub Release asset or another direct jar URL."

  log "Downloading panel jar..."
  mkdir -p "$APP_DIR/logs"

  local curl_args=(-fL --retry 3 --connect-timeout 20 --max-time 600 -o "$JAR_PATH")
  if [ -n "$GITHUB_TOKEN" ]; then
    curl_args+=(-H "Authorization: Bearer $GITHUB_TOKEN")
  fi
  curl "${curl_args[@]}" "$PANEL_JAR_URL"
  chmod 0644 "$JAR_PATH"
}

write_panel_config() {
  local generated_password="false"
  if [ -z "$PANEL_ADMIN_PASSWORD" ]; then
    PANEL_ADMIN_PASSWORD="$(random_password)"
    generated_password="true"
  fi

  if [ -z "$PANEL_PUBLIC_BASE_URL" ]; then
    if [ -z "$PUBLIC_IP" ]; then
      PUBLIC_IP="$(detect_public_ip)"
    fi
    [ -n "$PUBLIC_IP" ] || die "Cannot detect public IP. Set PUBLIC_IP or PANEL_PUBLIC_BASE_URL."
    PANEL_PUBLIC_BASE_URL="http://${PUBLIC_IP}:${PANEL_PORT}"
  fi

  log "Writing panel env file..."
  {
    write_env_line PANEL_PORT "$PANEL_PORT"
    write_env_line PANEL_ADMIN_USERNAME "$PANEL_ADMIN_USERNAME"
    write_env_line PANEL_ADMIN_PASSWORD "$PANEL_ADMIN_PASSWORD"
    write_env_line PANEL_PUBLIC_BASE_URL "$PANEL_PUBLIC_BASE_URL"
    write_env_line PANEL_DB_PATH "$PANEL_DB_PATH"
    write_env_line PANEL_LOG_FILE "$PANEL_LOG_FILE"
    write_env_line PANEL_DOCKER_HOST "$PANEL_DOCKER_HOST"
    write_env_line PANEL_MONITOR_TICK_MS "$PANEL_MONITOR_TICK_MS"
  } >"$ENV_FILE"
  chmod 600 "$ENV_FILE"

  if [ "$generated_password" = "true" ]; then
    printf '%s\n' "$PANEL_ADMIN_PASSWORD" >"$APP_DIR/admin-password.txt"
    chmod 600 "$APP_DIR/admin-password.txt"
  fi
}

write_systemd_service() {
  log "Writing systemd service..."
  cat >"/etc/systemd/system/${SERVICE_NAME}.service" <<EOF
[Unit]
Description=$APP_NAME
After=network-online.target docker.service
Wants=network-online.target docker.service

[Service]
User=root
WorkingDirectory=$APP_DIR
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/java -jar $JAR_PATH
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
}

start_panel() {
  log "Starting panel..."
  systemctl daemon-reload
  systemctl enable --now "$SERVICE_NAME"
  systemctl restart "$SERVICE_NAME"

  for _ in $(seq 1 60); do
    if curl -fsS "http://127.0.0.1:${PANEL_PORT}/login" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done

  systemctl --no-pager --full status "$SERVICE_NAME" | sed -n '1,18p' || true
}

print_summary() {
  log "Done."
  printf '\nPanel URL: %s/login\n' "$PANEL_PUBLIC_BASE_URL"
  printf 'Username: %s\n' "$PANEL_ADMIN_USERNAME"
  if [ -f "$APP_DIR/admin-password.txt" ]; then
    printf 'Password: %s\n' "$(cat "$APP_DIR/admin-password.txt")"
  else
    printf 'Password: configured by PANEL_ADMIN_PASSWORD\n'
  fi
  printf 'Env file: %s\n' "$ENV_FILE"
  printf 'Logs: %s and journalctl -u %s\n' "$PANEL_LOG_FILE" "$SERVICE_NAME"
}

main() {
  require_root
  require_supported_os
  install_packages
  enable_bbr
  configure_docker
  allow_panel_port
  download_panel_jar
  write_panel_config
  write_systemd_service
  start_panel
  print_summary
}

main "$@"
