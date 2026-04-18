#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${PROJECT_ROOT}"

SEGMENTATION_HOST="${SEGMENTATION_SERVICE_HOST:-127.0.0.1}"
SEGMENTATION_PORT="${SEGMENTATION_SERVICE_PORT:-8099}"
PROJECT_VENV_PYTHON="${PROJECT_ROOT}/.venv/bin/python"

log() {
  printf '[segmentation-service] %s\n' "$*"
}

fail() {
  printf '[segmentation-service] ERROR: %s\n' "$*" >&2
  exit 1
}

resolve_python() {
  if [[ -n "${VIRTUAL_ENV:-}" && -x "${VIRTUAL_ENV}/bin/python" ]]; then
    printf '%s\n' "${VIRTUAL_ENV}/bin/python"
    return 0
  fi

  if [[ -x "${PROJECT_VENV_PYTHON}" ]]; then
    printf '%s\n' "${PROJECT_VENV_PYTHON}"
    return 0
  fi

  fail "No Python virtual environment found. Activate one first or create ${PROJECT_VENV_PYTHON}."
}

build_process_matcher() {
  printf '%s\n' "uvicorn app.main:app --reload --host ${SEGMENTATION_HOST} --port ${SEGMENTATION_PORT}"
}

wait_for_process_exit() {
  local pid="$1"
  local timeout_checks="${2:-20}"
  local check

  for check in $(seq 1 "${timeout_checks}"); do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.2
  done

  return 1
}

stop_previous_run() {
  local process_matcher
  local matching_pids=()
  local pid

  process_matcher="$(build_process_matcher)"

  if command -v pgrep >/dev/null 2>&1; then
    while IFS= read -r pid; do
      [[ -n "${pid}" ]] && matching_pids+=("${pid}")
    done < <(pgrep -f "${process_matcher}" || true)
  fi

  if [[ "${#matching_pids[@]}" -eq 0 ]]; then
    return 0
  fi

  log "Stopping previous segmentation-service process(es): ${matching_pids[*]}"
  kill "${matching_pids[@]}" 2>/dev/null || true

  for pid in "${matching_pids[@]}"; do
    if wait_for_process_exit "${pid}"; then
      continue
    fi

    log "Force stopping stubborn process ${pid}"
    kill -9 "${pid}" 2>/dev/null || true
  done
}

ensure_port_available() {
  local port_details

  if command -v lsof >/dev/null 2>&1 && port_details="$(lsof -nP -iTCP:"${SEGMENTATION_PORT}" 2>/dev/null)"; then
    if [[ -n "${port_details}" ]]; then
      fail "Port ${SEGMENTATION_PORT} is still in use after cleanup:\n${port_details}"
    fi
  fi
}

ensure_runtime_dependencies() {
  local python_bin="$1"
  local missing_modules

  missing_modules="$(
    "${python_bin}" - <<'PY'
from importlib.util import find_spec

required = ("fastapi", "numpy", "PIL", "uvicorn", "torch")
missing = [name for name in required if find_spec(name) is None]
print(",".join(missing))
PY
  )"

  if [[ -n "${missing_modules}" ]]; then
    log "Installing missing runtime dependencies for modules: ${missing_modules}"
    "${python_bin}" -m pip install -e ".[unet]"
  fi
}

PYTHON_BIN="$(resolve_python)"

stop_previous_run
ensure_port_available
ensure_runtime_dependencies "${PYTHON_BIN}"

exec "${PYTHON_BIN}" -m uvicorn app.main:app --reload --host "${SEGMENTATION_HOST}" --port "${SEGMENTATION_PORT}" --app-dir .
