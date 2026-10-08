#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE="mysql:8.4"
DB_NAME="mulo_weather_it"
DB_USER="mulo_weather_it"
DB_PORT="3307"
CONTAINER_NAME="mulo-weather-it-${$}-${RANDOM}"
CONTAINER_ID=""
DB_PASSWORD=""
ROOT_PASSWORD=""

cleanup() {
  if [[ -n "${CONTAINER_ID}" ]]; then
    docker rm --force "${CONTAINER_ID}" >/dev/null 2>&1 || true
    printf 'Removed isolated MySQL container %s.\n' "${CONTAINER_NAME}"
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if ! command -v docker >/dev/null 2>&1; then
  printf 'Docker CLI is required for the isolated MySQL integration test.\n' >&2
  exit 2
fi
if ! command -v openssl >/dev/null 2>&1; then
  printf 'OpenSSL is required to generate an ephemeral test database password.\n' >&2
  exit 2
fi
if ! docker info >/dev/null 2>&1; then
  printf 'Docker daemon is unavailable. Start Docker and rerun this script.\n' >&2
  exit 2
fi

if docker ps --filter "publish=${DB_PORT}" --format '{{.ID}}' | grep -q .; then
  printf 'Port %s is already published by a running container; refusing to use it.\n' \
    "${DB_PORT}" >&2
  exit 2
fi

printf 'Starting disposable %s MySQL on 127.0.0.1:%s (database: %s).\n' \
  "${IMAGE}" "${DB_PORT}" "${DB_NAME}"
DB_PASSWORD="$(openssl rand -hex 24)"
ROOT_PASSWORD="$(openssl rand -hex 24)"
CONTAINER_ID="$(docker run --detach --rm \
  --name "${CONTAINER_NAME}" \
  --publish "127.0.0.1:${DB_PORT}:3306" \
  --env "MYSQL_DATABASE=${DB_NAME}" \
  --env "MYSQL_USER=${DB_USER}" \
  --env "MYSQL_PASSWORD=${DB_PASSWORD}" \
  --env "MYSQL_ROOT_PASSWORD=${ROOT_PASSWORD}" \
  "${IMAGE}")"

ready=false
for attempt in $(seq 1 60); do
  if docker exec --env "MYSQL_PWD=${ROOT_PASSWORD}" "${CONTAINER_ID}" \
      mysqladmin ping --host=127.0.0.1 --user=root --silent >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
if [[ "${ready}" != true ]]; then
  printf 'MySQL did not become ready within 120 seconds.\n' >&2
  docker logs "${CONTAINER_ID}" >&2 || true
  exit 1
fi

export MULO_WEATHER_IT_DB_URL="jdbc:mysql://127.0.0.1:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul"
export MULO_WEATHER_IT_DB_USERNAME="${DB_USER}"
export MULO_WEATHER_IT_DB_PASSWORD="${DB_PASSWORD}"

# Harmless test-only placeholders for any application properties not overridden by
# application-weather-it.yaml. No external client in this integration test is real.
export DB_URL="${MULO_WEATHER_IT_DB_URL}"
export DB_USERNAME="${DB_USER}"
export DB_PASSWORD="${DB_PASSWORD}"
export JWT_SECRET="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
export GCP_PROJECT_ID="mulo-weather-it"
export GCS_BUCKET_NAME="mulo-weather-it"
export KAKAO_REST_API_KEY="integration-test-no-network"
export KMA_SERVICE_KEY="integration-test-no-network"
export AI_INTERNAL_TOKEN="integration-test-no-network"
export AI_MOCK_ENABLED="true"

printf 'Running the complete Gradle test suite against the disposable database.\n'
cd "${ROOT_DIR}"
./gradlew test
