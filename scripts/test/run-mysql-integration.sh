#!/usr/bin/env bash
set -Eeuo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
container_name="mulo-integration-test-${$}-${RANDOM}"
container_started=false

cleanup() {
    if [[ "${container_started}" == true ]]; then
        docker rm --force "${container_name}" >/dev/null 2>&1 || true
    fi
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for command_name in docker java openssl; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
        printf '필수 명령을 찾을 수 없습니다: %s\n' "${command_name}" >&2
        exit 1
    fi
done

if ! docker info >/dev/null 2>&1; then
    printf 'Docker daemon에 연결할 수 없습니다.\n' >&2
    exit 1
fi

java_version="$(java -version 2>&1 | awk -F '"' '/version/ {print $2; exit}')"
if [[ "${java_version%%.*}" != 21 ]]; then
    printf 'Java 21이 필요합니다. 현재 버전: %s\n' "${java_version:-unknown}" >&2
    exit 1
fi

export MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
export MYSQL_PASSWORD="$(openssl rand -hex 24)"
export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
export MULO_INTEGRATION_TEST_DB_USERNAME="mulo_integration_test"
export MULO_INTEGRATION_TEST_DB_PASSWORD="${MYSQL_PASSWORD}"
export MULO_INTEGRATION_TEST_JWT_SECRET="$(openssl rand -base64 32)"

docker run --detach --name "${container_name}" \
    --publish 127.0.0.1::3306 \
    --env MYSQL_DATABASE=mulo_integration_test \
    --env MYSQL_USER=mulo_integration_test \
    --env MYSQL_PASSWORD \
    --env MYSQL_ROOT_PASSWORD \
    mysql:8.4 >/dev/null
container_started=true

mapped_address="$(docker port "${container_name}" 3306/tcp)"
mapped_port="${mapped_address##*:}"
export MULO_INTEGRATION_TEST_DB_URL="jdbc:mysql://127.0.0.1:${mapped_port}/mulo_integration_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul"

ready=false
for attempt in $(seq 1 90); do
    if docker exec --env MYSQL_PWD "${container_name}" \
        mysqladmin ping --host=127.0.0.1 --user=root --silent >/dev/null 2>&1; then
        ready=true
        break
    fi
    sleep 1
done

if [[ "${ready}" != true ]]; then
    printf '임시 MySQL 컨테이너가 제한 시간 안에 준비되지 않았습니다.\n' >&2
    exit 1
fi

unset MYSQL_PWD MYSQL_ROOT_PASSWORD MYSQL_PASSWORD
cd "${repository_root}"
SPRING_PROFILES_ACTIVE=integration-test ./gradlew integrationTest "$@"
