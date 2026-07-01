#!/usr/bin/env bash
set -Eeuo pipefail

SHA="${1:?Usage: deploy-blue-green.sh <git-sha>}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/spring-blog}"
DEPLOY_ENV="${DEPLOY_ENV:-${DEPLOY_ROOT}/config/deploy.env}"

if [[ -f "${DEPLOY_ENV}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${DEPLOY_ENV}"
  set +a
fi

IMAGE_NAME="${IMAGE_NAME:-spring-blog}"
IMAGE="${IMAGE_NAME}:${SHA}"
IMAGE_TAR="${IMAGE_TAR:-/tmp/spring-blog-${SHA}.tar}"
CONFIG_FILE="${CONFIG_FILE:-${DEPLOY_ROOT}/config/application-production.properties}"
REDIS_PASSWORD_FILE="${REDIS_PASSWORD_FILE:-${DEPLOY_ROOT}/config/redis-password}"
REDIS_DATA_DIR="${REDIS_DATA_DIR:-/mnt/spring-blog/redis}"
NETWORK="${NETWORK:-spring-blog-net}"
REDIS_CONTAINER="${REDIS_CONTAINER:-spring-blog-redis}"
BLUE_CONTAINER="${BLUE_CONTAINER:-spring-blog-blue}"
GREEN_CONTAINER="${GREEN_CONTAINER:-spring-blog-green}"
BLUE_PORT="${BLUE_PORT:-18080}"
GREEN_PORT="${GREEN_PORT:-18081}"
NGINX_TARGET_FILE="${NGINX_TARGET_FILE:-/etc/nginx/conf.d/spring-blog.target}"
ACTIVE_COLOR_FILE="${ACTIVE_COLOR_FILE:-${DEPLOY_ROOT}/state/active-color}"
PUBLIC_HEALTH_URL="${PUBLIC_HEALTH_URL:-}"
APP_MEMORY="${APP_MEMORY:-2g}"

for command_name in docker curl nginx systemctl flock; do
  command -v "${command_name}" >/dev/null || { echo "Missing command: ${command_name}" >&2; exit 1; }
done
for required_file in "${IMAGE_TAR}" "${CONFIG_FILE}" "${REDIS_PASSWORD_FILE}" "${NGINX_TARGET_FILE}"; do
  [[ -f "${required_file}" ]] || { echo "Missing file: ${required_file}" >&2; exit 1; }
done

mkdir -p "${DEPLOY_ROOT}/state" "${DEPLOY_ROOT}/logs" "${REDIS_DATA_DIR}"
exec 9>"${DEPLOY_ROOT}/state/deploy.lock"
flock -n 9 || { echo "Another deployment is running." >&2; exit 1; }

docker network inspect "${NETWORK}" >/dev/null 2>&1 || docker network create "${NETWORK}" >/dev/null

if ! docker container inspect "${REDIS_CONTAINER}" >/dev/null 2>&1; then
  docker run -d \
    --name "${REDIS_CONTAINER}" \
    --network "${NETWORK}" \
    --restart unless-stopped \
    -v "${REDIS_DATA_DIR}:/data" \
    -v "${REDIS_PASSWORD_FILE}:/run/secrets/redis-password:ro" \
    --health-cmd='REDISCLI_AUTH="$(cat /run/secrets/redis-password)" redis-cli ping' \
    --health-interval=10s \
    --health-timeout=3s \
    --health-retries=5 \
    redis:7.4-alpine sh -c 'exec redis-server --appendonly yes --requirepass "$(cat /run/secrets/redis-password)"' >/dev/null
elif [[ "$(docker inspect -f '{{.State.Running}}' "${REDIS_CONTAINER}")" != "true" ]]; then
  docker start "${REDIS_CONTAINER}" >/dev/null
fi

for _ in $(seq 1 30); do
  if docker exec "${REDIS_CONTAINER}" sh -c 'REDISCLI_AUTH="$(cat /run/secrets/redis-password)" redis-cli ping' 2>/dev/null | grep -q PONG; then
    REDIS_READY=true
    break
  fi
  sleep 2
done
[[ "${REDIS_READY:-false}" == "true" ]] || { echo "Redis health check failed." >&2; exit 1; }

docker load -i "${IMAGE_TAR}" >/dev/null
docker image inspect "${IMAGE}" >/dev/null

ACTIVE_COLOR=""
if [[ -f "${ACTIVE_COLOR_FILE}" ]]; then
  ACTIVE_COLOR="$(tr -d '[:space:]' < "${ACTIVE_COLOR_FILE}")"
fi
if [[ -z "${ACTIVE_COLOR}" ]]; then
  if grep -q "127.0.0.1:${BLUE_PORT}" "${NGINX_TARGET_FILE}" && docker ps --format '{{.Names}}' | grep -qx "${BLUE_CONTAINER}"; then
    ACTIVE_COLOR="blue"
  elif grep -q "127.0.0.1:${GREEN_PORT}" "${NGINX_TARGET_FILE}" && docker ps --format '{{.Names}}' | grep -qx "${GREEN_CONTAINER}"; then
    ACTIVE_COLOR="green"
  fi
fi
if [[ "${ACTIVE_COLOR}" == "blue" ]] && ! docker ps --format '{{.Names}}' | grep -qx "${BLUE_CONTAINER}"; then
  ACTIVE_COLOR=""
elif [[ "${ACTIVE_COLOR}" == "green" ]] && ! docker ps --format '{{.Names}}' | grep -qx "${GREEN_CONTAINER}"; then
  ACTIVE_COLOR=""
fi

if [[ "${ACTIVE_COLOR}" == "blue" ]]; then
  ACTIVE_CONTAINER="${BLUE_CONTAINER}"
  NEW_COLOR="green"
  NEW_CONTAINER="${GREEN_CONTAINER}"
  NEW_PORT="${GREEN_PORT}"
elif [[ "${ACTIVE_COLOR}" == "green" ]]; then
  ACTIVE_CONTAINER="${GREEN_CONTAINER}"
  NEW_COLOR="blue"
  NEW_CONTAINER="${BLUE_CONTAINER}"
  NEW_PORT="${BLUE_PORT}"
else
  ACTIVE_CONTAINER=""
  NEW_COLOR="blue"
  NEW_CONTAINER="${BLUE_CONTAINER}"
  NEW_PORT="${BLUE_PORT}"
fi

docker rm -f "${NEW_CONTAINER}" >/dev/null 2>&1 || true

TARGET_BACKUP="$(mktemp)"
cp "${NGINX_TARGET_FILE}" "${TARGET_BACKUP}"
DEPLOYMENT_SUCCEEDED=false
NGINX_SWITCHED=false

cleanup() {
  status=$?
  rm -f "${IMAGE_TAR}"
  if [[ "${DEPLOYMENT_SUCCEEDED}" != "true" ]]; then
    if [[ "${NGINX_SWITCHED}" == "true" ]]; then
      cp "${TARGET_BACKUP}" "${NGINX_TARGET_FILE}"
      nginx -t >/dev/null 2>&1 && systemctl reload nginx || true
    fi
    if docker container inspect "${NEW_CONTAINER}" >/dev/null 2>&1; then
      docker logs "${NEW_CONTAINER}" > "${DEPLOY_ROOT}/logs/${NEW_CONTAINER}-${SHA}.log" 2>&1 || true
      docker rm -f "${NEW_CONTAINER}" >/dev/null 2>&1 || true
    fi
  fi
  rm -f "${TARGET_BACKUP}"
  exit "${status}"
}
trap cleanup EXIT

docker run -d \
  --name "${NEW_CONTAINER}" \
  --network "${NETWORK}" \
  --restart unless-stopped \
  --stop-timeout 35 \
  --memory "${APP_MEMORY}" \
  --memory-swap "${APP_MEMORY}" \
  --read-only \
  --tmpfs /tmp:rw,nosuid,nodev,noexec,size=256m \
  -p "127.0.0.1:${NEW_PORT}:8080" \
  -v "${CONFIG_FILE}:/run/config/application-production.properties:ro" \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/run/config/application-production.properties \
  --label spring-blog.role=application \
  --label "spring-blog.revision=${SHA}" \
  "${IMAGE}" >/dev/null

for _ in $(seq 1 60); do
  if curl -fsS --max-time 5 "http://127.0.0.1:${NEW_PORT}/api/posts?page=0&size=1" | grep -q '"success":true'; then
    APP_READY=true
    break
  fi
  sleep 5
done
[[ "${APP_READY:-false}" == "true" ]] || { echo "Application health check failed." >&2; exit 1; }

printf 'server 127.0.0.1:%s;\n' "${NEW_PORT}" > "${NGINX_TARGET_FILE}.tmp"
mv "${NGINX_TARGET_FILE}.tmp" "${NGINX_TARGET_FILE}"
NGINX_SWITCHED=true
nginx -t
systemctl reload nginx

if [[ -n "${PUBLIC_HEALTH_URL}" ]]; then
  curl -fsS --retry 5 --retry-delay 2 --max-time 10 "${PUBLIC_HEALTH_URL}" >/dev/null
fi

printf '%s\n' "${NEW_COLOR}" > "${ACTIVE_COLOR_FILE}.tmp"
mv "${ACTIVE_COLOR_FILE}.tmp" "${ACTIVE_COLOR_FILE}"

if [[ -n "${ACTIVE_CONTAINER}" ]] && docker container inspect "${ACTIVE_CONTAINER}" >/dev/null 2>&1; then
  docker stop --time 30 "${ACTIVE_CONTAINER}" >/dev/null || true
  docker rm "${ACTIVE_CONTAINER}" >/dev/null || true
fi

DEPLOYMENT_SUCCEEDED=true
echo "Deployment completed: ${NEW_COLOR} (${SHA}) on port ${NEW_PORT}"
