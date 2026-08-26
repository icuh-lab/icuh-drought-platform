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
  out=$(docker run --rm "icuh-local/$m:verify" 2>&1 | head -40 || true)
  echo "$out" | grep -q "Starting IcuhDrought\|Starting IcuhPlatform\|Spring Boot" \
    || { echo "FAIL: $m 이 Spring Boot 기동 로그를 내지 않았다"; echo "$out"; exit 1; }
  echo "OK: $m"
done

echo "ALL OK"
