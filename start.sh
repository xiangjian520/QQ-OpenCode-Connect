#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

OPENCODE_FLAG=""
MYSQL_FLAG=""
ALL_EXTERNAL=""
for arg in "$@"; do
  case "$arg" in
    --external-opencode) OPENCODE_FLAG="external" ;;
    --mysql-external) MYSQL_FLAG="external" ;;
    --no-docker) ALL_EXTERNAL="true" ;;
    -h|--help)
      cat <<'HELP'
用法: ./start.sh [选项]
  --external-opencode  不启动 OpenCode 容器，使用 OPENCODE_BASE_URL
  --mysql-external     不启动 MySQL 容器，使用 DB_URL
  --no-docker          跳过全部 Docker，连接外部 MySQL 与 OpenCode
HELP
      exit 0
      ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "已创建 .env。请先修改 WEB_ADMIN_PASSWORD、DB_PASSWORD、MYSQL_ROOT_PASSWORD、OPENCODE_SERVER_PASSWORD 和 ONEBOT_TOKEN，然后重新运行 ./start.sh"
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

OPENCODE_MODE="${OPENCODE_MODE:-docker}"
MYSQL_MODE="${MYSQL_MODE:-docker}"
[[ -n "$OPENCODE_FLAG" ]] && OPENCODE_MODE="$OPENCODE_FLAG"
[[ -n "$MYSQL_FLAG" ]] && MYSQL_MODE="$MYSQL_FLAG"
if [[ -n "$ALL_EXTERNAL" ]]; then
  OPENCODE_MODE=external
  MYSQL_MODE=external
fi

JAVA_MAJOR="$(java -version 2>&1 | awk -F '[".]' '/version/ {print $2; exit}')"
if [[ -z "${JAVA_MAJOR:-}" || "$JAVA_MAJOR" -lt 25 ]]; then
  echo "需要 Java 25，当前: $(java -version 2>&1 | head -1)" >&2
  exit 1
fi

docker_cmd=()
detect_docker() {
  if docker info >/dev/null 2>&1; then
    docker_cmd=(docker)
  elif command -v pkexec >/dev/null 2>&1 && [[ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]]; then
    docker_cmd=(pkexec docker)
  elif command -v sudo >/dev/null 2>&1; then
    docker_cmd=(sudo docker)
  else
    echo "Docker 不可用。请启动 Docker 服务，或使用 --no-docker。" >&2
    exit 1
  fi
}

wait_tcp() {
  local host="$1" port="$2" name="$3"
  for _ in $(seq 1 60); do
    if (exec 3<>"/dev/tcp/$host/$port") 2>/dev/null; then
      echo "$name 已就绪 ($host:$port)"
      return 0
    fi
    sleep 1
  done
  echo "$name 等待超时 ($host:$port)" >&2
  return 1
}

if [[ "$MYSQL_MODE" == "docker" || "$OPENCODE_MODE" == "docker" ]]; then
  detect_docker
fi

if [[ "$MYSQL_MODE" == "docker" ]]; then
  "${docker_cmd[@]}" compose up -d mysql
  wait_tcp 127.0.0.1 "${MYSQL_PORT:-3307}" MySQL
fi

if [[ "$OPENCODE_MODE" == "docker" ]]; then
  "${docker_cmd[@]}" compose up -d opencode
  opencode_user="${OPENCODE_SERVER_USERNAME:-opencode}"
  for _ in $(seq 1 60); do
    if curl -fsS -u "$opencode_user:$OPENCODE_SERVER_PASSWORD" "${OPENCODE_BASE_URL:-http://127.0.0.1:4096}/api/info" >/dev/null 2>&1; then
      echo "OpenCode 已就绪"
      break
    fi
    sleep 1
  done
  if ! curl -fsS -u "$opencode_user:$OPENCODE_SERVER_PASSWORD" "${OPENCODE_BASE_URL:-http://127.0.0.1:4096}/api/info" >/dev/null 2>&1; then
    echo "OpenCode 启动超时，请运行: ${docker_cmd[*]} compose logs opencode" >&2
    exit 1
  fi
fi

if [[ -x ./mvnw ]]; then
  mvn_cmd=(./mvnw)
elif command -v mvn >/dev/null 2>&1; then
  mvn_cmd=(mvn)
else
  echo "未找到 Maven。请安装 Maven 或运行 mvn wrapper:wrapper。" >&2
  exit 1
fi

echo "------------------------------------------------------------"
echo "Web 管理台: http://127.0.0.1:${WEB_PORT:-8080}"
echo "OneBot 地址: ws://<本机IP>:${WEB_PORT:-8080}/onebot/v11/ws"
if [[ "$OPENCODE_MODE" == "docker" ]]; then
  echo "首次使用请在新终端执行: ./scripts/opencode-auth.sh"
fi
echo "------------------------------------------------------------"
exec "${mvn_cmd[@]}" spring-boot:run
