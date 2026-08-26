#!/usr/bin/env bash
# 3개 모듈 이미지를 빌드하고, jar가 들어갔는지와 Spring Boot가 기동을 시작하는지 확인한다.
# DB가 없으므로 기동은 데이터소스 단계에서 실패하는 것이 정상이다.
set -euo pipefail

MODULES=(public-api admin-api open-api)

for m in "${MODULES[@]}"; do
  echo "=== build $m"
  docker build --build-arg "MODULE=$m" -t "icuh-local/$m:verify" .

  echo "=== jar 존재 확인 $m"
  docker run --rm --entrypoint sh "icuh-local/$m:verify" -c 'test -s /app/app.jar' \
    || { echo "FAIL: $m 이미지에 app.jar가 없다"; exit 1; }

  echo "=== 기동 확인 $m"
  cid=$(docker run -d "icuh-local/$m:verify")
  found=0
  for _ in $(seq 1 30); do
    if docker logs "$cid" 2>&1 | grep -q "Starting Icuh\|Spring Boot"; then found=1; break; fi
    sleep 1
  done
  logs=$(docker logs "$cid" 2>&1 | head -40 || true)
  docker rm -f "$cid" >/dev/null
  [ "$found" -eq 1 ] || { echo "FAIL: $m 이 Spring Boot 기동 로그를 내지 않았다"; echo "$logs"; exit 1; }
  echo "OK: $m"
done

echo "ALL OK"
