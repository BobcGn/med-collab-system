#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/.run/logs"
FRONTEND_DIR="${ROOT_DIR}/frontend"
SEGMENTATION_DIR="${ROOT_DIR}/segmentation-service"

BACKEND_SERVICES=("auth-service" "patient-service" "metric-service" "api-gateway")
BACKEND_PORTS=(8081 8082 8083 8088)

CLIENT_PORT="${CLIENT_PORT:-5173}"
SEGMENTATION_HOST="${SEGMENTATION_HOST:-127.0.0.1}"
SEGMENTATION_PORT="${SEGMENTATION_PORT:-8099}"
SEGMENTATION_ENV="${SEGMENTATION_ENV:-local}"
SEGMENTATION_BACKEND="${SEGMENTATION_BACKEND:-torch_unet}"
SEGMENTATION_PYTHON_BIN="${SEGMENTATION_PYTHON_BIN:-}"
SEGMENTATION_VENV_DIR="${SEGMENTATION_VENV_DIR:-${SEGMENTATION_DIR}/.venv}"
FRONTEND_MODE="preview"
SKIP_BUILD=false
START_SEGMENTATION=true

SEGMENTATION_HOST_PYTHON=""
SEGMENTATION_RUNTIME_PYTHON=""

PIDS=()
NAMES=()

usage() {
  cat <<'EOF'
Usage: scripts/quick-start.sh [options]

Options:
  --skip-build               Skip npm/gradle build and only start services.
  --frontend-mode <mode>     Frontend startup mode: preview (default) or dev.
  --without-segmentation     Do not start the Python segmentation-service.
  --segmentation-backend     Segmentation backend: torch_unet (default) or mock.
  --segmentation-python      Python executable for segmentation-service (must be >= 3.11).
  -h, --help                 Show this help.

Environment:
  CLIENT_PORT                Frontend port (default: 5173).
  SEGMENTATION_HOST          Segmentation-service host (default: 127.0.0.1).
  SEGMENTATION_PORT          Segmentation-service port (default: 8099).
  SEGMENTATION_ENV           Segmentation-service environment (default: local).
  SEGMENTATION_BACKEND       Segmentation backend (default: torch_unet).
  SEGMENTATION_PYTHON_BIN    Python executable for segmentation-service.
  SEGMENTATION_VENV_DIR      Virtualenv directory for segmentation-service.
EOF
}

log() {
  printf '[quick-start] %s\n' "$*"
}

fail() {
  printf '[quick-start] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || fail "Missing required command: ${cmd}"
}

ensure_port_free() {
  local port="$1"
  local name="$2"
  if command -v lsof >/dev/null 2>&1 && lsof -iTCP:"${port}" -sTCP:LISTEN -t >/dev/null 2>&1; then
    fail "Port ${port} is already in use (${name}). Please stop the existing process first."
  fi
}

is_port_open() {
  local port="$1"

  if command -v nc >/dev/null 2>&1; then
    nc -z 127.0.0.1 "${port}" >/dev/null 2>&1
    return $?
  fi

  (echo >"/dev/tcp/127.0.0.1/${port}") >/dev/null 2>&1
}

cleanup() {
  local code="$?"
  trap - INT TERM EXIT
  if [[ "${#PIDS[@]}" -gt 0 ]]; then
    log "Stopping started processes..."
    kill "${PIDS[@]}" 2>/dev/null || true
    wait "${PIDS[@]}" 2>/dev/null || true
  fi
  exit "${code}"
}

wait_for_port() {
  local name="$1"
  local pid="$2"
  local port="$3"
  local log_file="$4"

  for _ in $(seq 1 90); do
    if is_port_open "${port}"; then
      log "${name} is ready at http://localhost:${port}"
      return 0
    elif grep -Eq "Responding at|Local:" "${log_file}" 2>/dev/null; then
      log "${name} started (log-based readiness check)."
      return 0
    fi

    if ! kill -0 "${pid}" 2>/dev/null; then
      log "${name} exited during startup. Recent logs:"
      tail -n 50 "${log_file}" >&2 || true
      fail "${name} failed to start"
    fi
    sleep 1
  done

  log "${name} startup timed out. Recent logs:"
  tail -n 50 "${log_file}" >&2 || true
  fail "${name} did not become ready on port ${port}"
}

start_process() {
  local name="$1"
  local port="$2"
  local log_file="${LOG_DIR}/${name}.log"
  shift 2

  log "Starting ${name}..."
  "$@" >"${log_file}" 2>&1 &
  local pid="$!"
  PIDS+=("${pid}")
  NAMES+=("${name}")
  log "${name} PID=${pid}, log=${log_file}"

  wait_for_port "${name}" "${pid}" "${port}" "${log_file}"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --skip-build)
        SKIP_BUILD=true
        shift
        ;;
      --frontend-mode)
        [[ $# -ge 2 ]] || fail "Missing value for --frontend-mode"
        FRONTEND_MODE="$2"
        shift 2
        ;;
      --frontend-mode=*)
        FRONTEND_MODE="${1#*=}"
        shift
        ;;
      --without-segmentation)
        START_SEGMENTATION=false
        shift
        ;;
      --segmentation-backend)
        [[ $# -ge 2 ]] || fail "Missing value for --segmentation-backend"
        SEGMENTATION_BACKEND="$2"
        shift 2
        ;;
      --segmentation-backend=*)
        SEGMENTATION_BACKEND="${1#*=}"
        shift
        ;;
      --segmentation-python)
        [[ $# -ge 2 ]] || fail "Missing value for --segmentation-python"
        SEGMENTATION_PYTHON_BIN="$2"
        shift 2
        ;;
      --segmentation-python=*)
        SEGMENTATION_PYTHON_BIN="${1#*=}"
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        fail "Unknown argument: $1"
        ;;
    esac
  done

  if [[ "${FRONTEND_MODE}" != "preview" && "${FRONTEND_MODE}" != "dev" ]]; then
    fail "--frontend-mode must be either 'preview' or 'dev'"
  fi

  if [[ "${SEGMENTATION_BACKEND}" != "mock" && "${SEGMENTATION_BACKEND}" != "torch_unet" ]]; then
    fail "--segmentation-backend must be either 'mock' or 'torch_unet'"
  fi
}

build_frontend() {
  log "Installing frontend dependencies..."
  pushd "${FRONTEND_DIR}" >/dev/null
  if [[ -f "package-lock.json" ]]; then
    npm ci --no-audit --no-fund
  else
    npm install --no-audit --no-fund
  fi

  log "Building frontend..."
  npm run build
  popd >/dev/null
}

build_backend() {
  log "Building backend distributions via Gradle..."
  pushd "${ROOT_DIR}" >/dev/null
  ./gradlew --parallel \
    :auth-service:installDist \
    :patient-service:installDist \
    :metric-service:installDist \
    :api-gateway:installDist \
    -x test
  popd >/dev/null
}

resolve_segmentation_python() {
  local candidate=""

  if [[ -n "${SEGMENTATION_PYTHON_BIN}" ]]; then
    candidate="${SEGMENTATION_PYTHON_BIN}"
  elif command -v python3.11 >/dev/null 2>&1; then
    candidate="$(command -v python3.11)"
  elif command -v python3 >/dev/null 2>&1; then
    candidate="$(command -v python3)"
  fi

  [[ -n "${candidate}" ]] || fail "Missing Python 3.11+ for segmentation-service"

  if ! "${candidate}" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)' >/dev/null 2>&1; then
    fail "segmentation-service requires Python >= 3.11 (current: ${candidate})"
  fi

  SEGMENTATION_HOST_PYTHON="${candidate}"
}

prepare_segmentation_service() {
  if [[ "${START_SEGMENTATION}" != "true" ]]; then
    return 0
  fi

  [[ -d "${SEGMENTATION_DIR}" ]] || fail "Cannot find segmentation-service directory"

  resolve_segmentation_python
  ensure_port_free "${SEGMENTATION_PORT}" "segmentation-service"

  if [[ "${SKIP_BUILD}" == "false" ]]; then
    if [[ ! -x "${SEGMENTATION_VENV_DIR}/bin/python" ]]; then
      log "Creating segmentation-service virtual environment at ${SEGMENTATION_VENV_DIR}..."
      "${SEGMENTATION_HOST_PYTHON}" -m venv "${SEGMENTATION_VENV_DIR}"
    fi

    local pip_bin="${SEGMENTATION_VENV_DIR}/bin/pip"
    [[ -x "${pip_bin}" ]] || fail "Missing pip in segmentation-service virtual environment: ${pip_bin}"

    log "Installing segmentation-service dependencies..."
    pushd "${SEGMENTATION_DIR}" >/dev/null
    if [[ "${SEGMENTATION_BACKEND}" == "torch_unet" ]]; then
      "${pip_bin}" install -e ".[unet]"
    else
      "${pip_bin}" install -e .
    fi
    popd >/dev/null
  fi

  SEGMENTATION_RUNTIME_PYTHON="${SEGMENTATION_VENV_DIR}/bin/python"
  [[ -x "${SEGMENTATION_RUNTIME_PYTHON}" ]] || fail "Missing segmentation-service runtime Python: ${SEGMENTATION_RUNTIME_PYTHON}"
}

start_backend_services() {
  local i
  for i in "${!BACKEND_SERVICES[@]}"; do
    local service="${BACKEND_SERVICES[$i]}"
    local port="${BACKEND_PORTS[$i]}"
    local bin_path="${ROOT_DIR}/${service}/build/install/${service}/bin/${service}"

    [[ -x "${bin_path}" ]] || fail "Missing executable for ${service}: ${bin_path}"
    ensure_port_free "${port}" "${service}"
    start_process "${service}" "${port}" "${bin_path}"
  done
}

start_segmentation_service() {
  if [[ "${START_SEGMENTATION}" != "true" ]]; then
    log "Skipping segmentation-service startup."
    return 0
  fi

  pushd "${SEGMENTATION_DIR}" >/dev/null
  start_process \
    "segmentation-service" \
    "${SEGMENTATION_PORT}" \
    env \
    SEGMENTATION_SERVICE_ENVIRONMENT="${SEGMENTATION_ENV}" \
    SEGMENTATION_SERVICE_HOST="${SEGMENTATION_HOST}" \
    SEGMENTATION_SERVICE_PORT="${SEGMENTATION_PORT}" \
    SEGMENTATION_SERVICE_INFERENCE_BACKEND="${SEGMENTATION_BACKEND}" \
    "${SEGMENTATION_RUNTIME_PYTHON}" \
    -m uvicorn app.main:app --host "${SEGMENTATION_HOST}" --port "${SEGMENTATION_PORT}" --app-dir .
  popd >/dev/null
}

start_frontend() {
  ensure_port_free "${CLIENT_PORT}" "frontend"
  pushd "${FRONTEND_DIR}" >/dev/null
  if [[ "${FRONTEND_MODE}" == "preview" ]]; then
    start_process "frontend-preview" "${CLIENT_PORT}" npm run preview -- --host 0.0.0.0 --port "${CLIENT_PORT}"
  else
    start_process "frontend-dev" "${CLIENT_PORT}" npm run dev -- --host 0.0.0.0 --port "${CLIENT_PORT}"
  fi
  popd >/dev/null
}

watch_processes() {
  log "All services are running. Press Ctrl+C to stop everything."
  log "Backend: 8081(auth), 8082(patient), 8083(metric), 8088(gateway)"
  if [[ "${START_SEGMENTATION}" == "true" ]]; then
    log "Segmentation: http://localhost:${SEGMENTATION_PORT} (backend=${SEGMENTATION_BACKEND})"
  fi
  log "Frontend: http://localhost:${CLIENT_PORT}"

  while true; do
    local i
    for i in "${!PIDS[@]}"; do
      if ! kill -0 "${PIDS[$i]}" 2>/dev/null; then
        fail "${NAMES[$i]} exited unexpectedly. Check ${LOG_DIR}/${NAMES[$i]}.log"
      fi
    done
    sleep 3
  done
}

main() {
  parse_args "$@"
  trap cleanup INT TERM EXIT

  require_cmd java
  require_cmd node
  require_cmd npm

  [[ -x "${ROOT_DIR}/gradlew" ]] || fail "Cannot find executable gradlew in project root"
  [[ -d "${FRONTEND_DIR}" ]] || fail "Cannot find frontend directory"

  mkdir -p "${LOG_DIR}"

  if [[ "${SKIP_BUILD}" == "false" ]]; then
    build_frontend
    build_backend
  fi

  prepare_segmentation_service
  start_backend_services
  start_segmentation_service
  start_frontend
  watch_processes
}

main "$@"
