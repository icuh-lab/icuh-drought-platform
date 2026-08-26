# CI/CD → EC2 배포 파이프라인 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `main`에 머지되면 3개 실행 모듈(public-api·admin-api·open-api)이 GHCR 이미지로 빌드되어 기존 EC2 한 대에 docker compose로 무인 배포되고, 실패 시 직전 성공 이미지로 자동 롤백되는 파이프라인을 만든다.

**Architecture:** CI에서 `./gradlew build`를 **한 번** 돌려 jar 3개를 만들고, Dockerfile은 런타임 스테이지만 두어 jar를 복사만 한다(이전 프로젝트는 컨테이너 안에서 gradle 빌드를 반복했다). 이미지는 커밋 SHA로 태깅해 GHCR에 올리고, 배포는 Actions가 보안그룹 22번을 한시적으로 열어 SSH로 들어가 `docker compose pull && up -d`만 실행한다. 앱 환경변수 20여 개는 EC2의 `/opt/icuh/.env`(600)에 상주하고 Actions는 이미지 태그 하나만 넘긴다.

**Tech Stack:** GitHub Actions · GHCR · Docker / docker compose v2 · eclipse-temurin:17-jre · AWS EC2 + 보안그룹 · Gradle 8.13 / JDK 17

**Spec:** 별도 스펙 문서 없음. 승인된 설계를 아래 "설계 요약"에 옮겨 담았다. 실행자는 이 문서만 읽으면 된다.

## Global Constraints

- **JDK는 17로 고정한다.** 로컬/러너에 JDK 25가 잡히면 Gradle 8.13이 `Could not create task of type 'Test'`로 죽는다.
- **배포 대상은 3개 모듈뿐이다**: `public-api`(8081) · `admin-api`(8082) · `open-api`(8083). `batch`는 `IcuhDroughtBatchApplication.java` 하나뿐이고 Job 정의가 없어 이번 범위에서 제외한다.
- **이미지 이름**: `ghcr.io/icuh-lab/icuh-drought-platform/<module>` (소문자 고정).
- **이미지 태그**: `${{ github.sha }}` 전체 40자 + `latest` 병행. 배포는 항상 SHA 태그로 한다.
- **8082(admin-api)는 보안그룹에 열지 않는다.** admin-api는 인증이 전혀 없어 결재 승인·반려·병합 API가 무방비다. 관리자는 SSH 터널로 접근한다.
- **`SPRING_PROFILES_ACTIVE=prod`를 반드시 준다.** public-api의 기본 프로필은 `local`, open-api는 `dev`다. admin-api에는 `application-prod.yml`이 없지만 설정이 전부 환경변수 기반이라 동작에 문제없다.
- 시크릿은 **명령줄에 싣지 않는다.** 이전 파이프라인이 `docker run -e PASSWORD='...'`를 SSH 명령줄에 인라인해 EC2의 `ps`와 셸 히스토리에 평문으로 남겼다.
- 저장소: `git@github.com:icuh-lab/icuh-drought-platform.git`, 기본 브랜치 `main`.

## 설계 요약

이전 프로젝트(`icuh-platform`) 파이프라인 대비 바뀌는 지점:

| 지점 | 이전 | 이번 |
|---|---|---|
| 테스트 | `build -x test` (게이트 없음) | `./gradlew build` (테스트 포함) |
| 이미지 태그 | `:latest` 고정 | 커밋 SHA + latest |
| 빌드 횟수 | CI 1회 + Dockerfile 안 1회 = 2회 | CI 1회 |
| 레지스트리 | Docker Hub (시크릿 3개) | GHCR (`GITHUB_TOKEN`, 시크릿 0개) |
| 시크릿 전달 | `docker run -e` 인라인 | EC2 `.env` 상주 |
| 배포 명령 | `stop → rm → run` | `compose pull && up -d` |
| 롤백 | 불가 | `.last-good` 태그로 자동/수동 |
| PR 동작 | PR에서도 EC2 배포 | PR은 검증만 |

EC2 파일 배치:

```
/opt/icuh/
├── docker-compose.yml   ← 저장소에서 관리, 배포마다 scp로 갱신
├── .env                 ← 앱 환경변수. 600, git 미포함, 수동 관리
├── .last-good           ← 마지막 헬스체크 통과 SHA
└── logs/{public,admin,open}/
```

---

## Task 1: Dockerfile과 빌드 컨텍스트

3개 모듈이 하나의 Dockerfile을 `--build-arg MODULE=`로 공유한다. jar는 CI가 만든 것을 복사만 한다.

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`

**Interfaces:**
- Consumes: `<module>/build/libs/<module>-0.0.1-SNAPSHOT.jar` — `./gradlew build`가 만든다.
- Produces: `MODULE` build-arg를 받는 이미지. 컨테이너 안 경로는 `/app/app.jar`. 이후 Task 2의 compose가 이 이미지를 참조한다.

- [ ] **Step 1: 검증용 스크립트를 먼저 만든다 (실패하는 테스트)**

`scripts/verify-images.sh` 생성:

```bash
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
```

실행 권한: `chmod +x scripts/verify-images.sh`

- [ ] **Step 2: 실패를 확인한다**

Run: `./scripts/verify-images.sh`
Expected: FAIL — `Dockerfile`이 없어 `docker build`가 `failed to read dockerfile`로 죽는다.

- [ ] **Step 3: `.dockerignore`를 만든다**

빌드 컨텍스트에 jar만 넣는다. 저장소 루트를 컨텍스트로 쓰면 `.git`과 모든 모듈의 `build/`가 통째로 업로드되어 느려진다.

```
*
!*/build/libs/*-SNAPSHOT.jar
```

`*-SNAPSHOT.jar` 글롭은 Spring Boot 실행 jar만 잡는다. 같은 디렉터리의 `*-SNAPSHOT-plain.jar`는 `-plain.jar`로 끝나므로 매칭되지 않는다.

- [ ] **Step 4: `Dockerfile`을 만든다**

```dockerfile
# 3개 실행 모듈이 공유한다. jar는 CI에서 ./gradlew build로 만든 것을 복사만 한다.
# 로컬에서 쓰려면 docker build 전에 ./gradlew build 가 선행되어야 한다.
FROM eclipse-temurin:17-jre

ARG MODULE
WORKDIR /app

COPY ${MODULE}/build/libs/*-SNAPSHOT.jar app.jar

# 컨테이너 메모리 한도를 기준으로 힙을 잡는다. EC2 한 대에 JVM 3개가 뜨므로 여유를 남긴다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 5: jar를 만들고 검증을 통과시킨다**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ./gradlew build
./scripts/verify-images.sh
```

Expected: `ALL OK`

- [ ] **Step 6: 로컬 이미지를 정리한다**

```bash
docker rmi icuh-local/public-api:verify icuh-local/admin-api:verify icuh-local/open-api:verify
```

- [ ] **Step 7: 커밋**

```bash
git add Dockerfile .dockerignore scripts/verify-images.sh
git commit -m "build: 3개 실행 모듈이 공유하는 런타임 Dockerfile 추가"
```

---

## Task 2: EC2 compose 구성과 환경변수 템플릿

**Files:**
- Create: `deploy/docker-compose.yml`
- Create: `deploy/.env.example`
- Create: `deploy/README.md`

**Interfaces:**
- Consumes: Task 1의 이미지 이름 규칙 `ghcr.io/icuh-lab/icuh-drought-platform/<module>`.
- Produces: EC2 `/opt/icuh/docker-compose.yml`이 될 파일. 서비스 이름은 `public-api`·`admin-api`·`open-api`. 외부에서 `IMAGE_TAG` 환경변수 하나만 주입받는다. Task 5의 런북과 Task 6의 배포 job이 이 파일을 쓴다.

- [ ] **Step 1: 렌더링 검증을 먼저 시도한다 (실패하는 테스트)**

Run:
```bash
IMAGE_TAG=testsha docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config
```
Expected: FAIL — `no such file or directory`.

- [ ] **Step 2: `deploy/.env.example`을 만든다**

실제 값이 아닌 자리표시자만 넣는다. 이 파일은 커밋되고, 실제 `.env`는 EC2에만 둔다.

```bash
# EC2 /opt/icuh/.env 템플릿. 실제 값을 채워 600 권한으로 두고 절대 커밋하지 않는다.
# 항목 근거: RUNTIME_CONFIG.md

SPRING_PROFILES_ACTIVE=prod

# --- public-api (8081) ---
PUBLIC_PROD_DB_URL=jdbc:mysql://CHANGEME:3306/CHANGEME
PUBLIC_PROD_DB_USERNAME=CHANGEME
PUBLIC_PROD_DB_PASSWORD=CHANGEME
PUBLIC_CORS_ALLOWED_ORIGINS=https://CHANGEME
PUBLIC_S3_BUCKET_NAME=CHANGEME
PUBLIC_AWS_REGION=ap-northeast-2
PUBLIC_AWS_ACCESS_KEY_ID=CHANGEME
PUBLIC_AWS_SECRET_ACCESS_KEY=CHANGEME

# --- admin-api (8082, 보안그룹 미개방) ---
ADMIN_DB_URL=jdbc:mysql://CHANGEME:3306/ACTUAL_DRGHT
ADMIN_DB_USERNAME=CHANGEME
ADMIN_DB_PASSWORD=CHANGEME
ADMIN_CORS_ALLOWED_ORIGINS=http://localhost:3000
ADMIN_S3_BUCKET_NAME=CHANGEME
ADMIN_AWS_REGION=ap-northeast-2
ADMIN_AWS_ACCESS_KEY_ID=CHANGEME
ADMIN_AWS_SECRET_ACCESS_KEY=CHANGEME

# --- open-api (8083) ---
OPEN_API_DB_URL=jdbc:mysql://CHANGEME:3306/ACTUAL_DRGHT
OPEN_API_DB_USERNAME=CHANGEME
OPEN_API_DB_PASSWORD=CHANGEME
OPEN_API_CORS_ALLOWED_ORIGINS=https://CHANGEME
```

- [ ] **Step 3: `deploy/docker-compose.yml`을 만든다**

```yaml
# EC2 /opt/icuh/docker-compose.yml 로 배치된다.
# IMAGE_TAG만 배포 시점에 셸 환경변수로 주입받고, 앱 환경변수는 같은 디렉터리의 .env에서 읽는다.

services:
  public-api:
    image: ghcr.io/icuh-lab/icuh-drought-platform/public-api:${IMAGE_TAG}
    container_name: icuh-public-api
    env_file: .env
    ports:
      - "8081:8081"
    volumes:
      - ./logs/public:/root/log/spring
    restart: unless-stopped

  admin-api:
    image: ghcr.io/icuh-lab/icuh-drought-platform/admin-api:${IMAGE_TAG}
    container_name: icuh-admin-api
    env_file: .env
    # 인증이 없는 앱이다. 호스트 루프백에만 바인딩해 외부에서 직접 닿지 못하게 한다.
    ports:
      - "127.0.0.1:8082:8082"
    volumes:
      - ./logs/admin:/root/log/spring
    restart: unless-stopped

  open-api:
    image: ghcr.io/icuh-lab/icuh-drought-platform/open-api:${IMAGE_TAG}
    container_name: icuh-open-api
    env_file: .env
    ports:
      - "8083:8083"
    volumes:
      - ./logs/open:/root/log/spring
    restart: unless-stopped
```

admin-api는 `127.0.0.1:8082:8082`로 묶는다. 보안그룹 설정과 무관하게 호스트 밖에서 닿지 않으므로 방어가 두 겹이 된다.

- [ ] **Step 4: 렌더링을 확인한다**

Run:
```bash
IMAGE_TAG=testsha docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config | grep -E "image:|published:"
```
Expected: 3개 이미지가 `:testsha` 태그로, 포트가 `8081` / `127.0.0.1:8082` / `8083`으로 렌더링된다.

- [ ] **Step 5: `deploy/README.md`를 만든다**

```markdown
# 배포 구성

EC2 한 대에 public-api(8081) · admin-api(8082) · open-api(8083) 세 컨테이너를 올린다.

## 파일

| 파일 | 위치 | 관리 |
|---|---|---|
| `docker-compose.yml` | 저장소 → 배포마다 EC2 `/opt/icuh/`로 scp | git |
| `.env` | EC2 `/opt/icuh/.env` (600) | 수동. `.env.example` 참고 |
| `.last-good` | EC2 `/opt/icuh/.last-good` | 배포 워크플로가 갱신 |

## 로컬에서 이미지 빌드

Dockerfile은 CI가 만든 jar를 복사만 하므로 gradle 빌드가 선행되어야 한다.

```bash
./gradlew build
docker build --build-arg MODULE=public-api -t icuh-local/public-api .
```

## admin-api 접근

인증이 없는 앱이라 8082는 보안그룹에도 열지 않고 컨테이너도 루프백에만 바인딩한다.
접근이 필요하면 SSH 터널을 쓴다.

```bash
ssh -i <키> -L 8082:localhost:8082 <user>@<host>
# 브라우저에서 http://localhost:8082
```

## 롤백

Actions → deploy 워크플로 → Run workflow → `image_tag`에 되돌릴 커밋 SHA 입력.
```

- [ ] **Step 6: 커밋**

```bash
git add deploy/
git commit -m "build: EC2 compose 구성과 환경변수 템플릿 추가"
```

---

## Task 3: CI 워크플로

PR과 브랜치 push에서 빌드와 테스트만 돌린다. 배포는 하지 않는다.

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: `ci` 워크플로. 이후 Task 4의 deploy 워크플로와 job을 공유하지 않는다(중복 빌드를 감수하고 관심사를 분리한다).

- [ ] **Step 1: YAML 문법 검증을 먼저 시도한다 (실패하는 테스트)**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"`
Expected: FAIL — `FileNotFoundError`.

- [ ] **Step 2: `.github/workflows/ci.yml`을 만든다**

```yaml
name: CI

on:
  pull_request:
  push:
    branches-ignore: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read

    steps:
      - uses: actions/checkout@v4

      # Gradle 8.13은 JDK 25에서 기동하지 못한다. 17로 고정한다.
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      # 이전 파이프라인은 -x test 였다. 여기가 실제 게이트다.
      - name: Build and test
        run: ./gradlew build --console=plain

      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/build/reports/tests/test'
          retention-days: 7
```

- [ ] **Step 3: 문법을 확인한다**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('OK')"`
Expected: `OK`

- [ ] **Step 4: 커밋하고 푸시해 실제 실행을 확인한다**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: PR·브랜치 push에서 빌드와 테스트를 돌리는 워크플로 추가"
git push
```

GitHub → Actions 탭에서 `CI` 실행이 초록인지 확인한다. 붉으면 로그를 보고 고친 뒤 다시 푸시한다. 여기서 통과해야 다음 태스크로 넘어간다.

---

## Task 4: 이미지 빌드·푸시 워크플로

배포는 아직 붙이지 않는다. GHCR에 이미지가 올라가는 것까지만 검증한다.

**Files:**
- Create: `.github/workflows/deploy.yml`

**Interfaces:**
- Consumes: Task 1의 `Dockerfile`(build-arg `MODULE`).
- Produces: `build-and-push` job과 `ghcr.io/icuh-lab/icuh-drought-platform/{public-api,admin-api,open-api}:<sha>` 이미지. Task 6의 `deploy` job은 이 job의 출력을 받지 않고 `inputs.image_tag`와 `github.sha`로 태그를 직접 해석한다.

- [ ] **Step 1: `.github/workflows/deploy.yml`을 만든다 (build-and-push만)**

```yaml
name: Deploy

on:
  push:
    branches: [ main ]
  workflow_dispatch:
    inputs:
      image_tag:
        description: '되돌릴 커밋 SHA. 비우면 현재 커밋으로 배포한다.'
        required: false
        type: string

jobs:
  build-and-push:
    # 롤백 실행일 때는 이미 올라간 이미지를 쓰므로 빌드를 건너뛴다.
    if: github.event_name == 'push' || inputs.image_tag == ''
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      # 한 번만 돌린다. 3개 모듈의 jar가 모두 나온다.
      - name: Build and test
        run: ./gradlew build --console=plain

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # jar 복사뿐이라 순차 빌드로도 수 초다. 매트릭스로 나누면 러너마다 gradle 빌드를 반복해 더 느려진다.
      - name: Build and push images
        env:
          SHA: ${{ github.sha }}
        run: |
          set -euo pipefail
          for m in public-api admin-api open-api; do
            base="ghcr.io/icuh-lab/icuh-drought-platform/${m}"
            docker build --build-arg "MODULE=${m}" \
              -t "${base}:${SHA}" -t "${base}:latest" .
            docker push "${base}:${SHA}"
            docker push "${base}:latest"
          done
```

- [ ] **Step 2: 문법을 확인한다**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/deploy.yml')); print('OK')"`
Expected: `OK`

- [ ] **Step 3: 저장소 설정을 확인한다**

GitHub → Settings → Actions → General → Workflow permissions 에서 `Read and write permissions`가 켜져 있는지 확인한다. 꺼져 있으면 `packages: write`가 무시되어 푸시가 403으로 실패한다.

- [ ] **Step 4: 커밋하고 main에 반영해 실행을 확인한다**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: 3개 모듈 이미지를 GHCR에 빌드·푸시하는 워크플로 추가"
```

PR을 만들어 머지하거나 `main`에 직접 푸시한다. Actions에서 `Deploy` 실행이 초록인지 확인하고, GitHub 저장소 우측 Packages에 `public-api`·`admin-api`·`open-api` 세 패키지가 생겼는지 본다.

- [ ] **Step 5: 로컬에서 pull이 되는지 확인한다**

```bash
echo <GitHub PAT(read:packages)> | docker login ghcr.io -u <github-id> --password-stdin
docker pull ghcr.io/icuh-lab/icuh-drought-platform/open-api:latest
docker logout ghcr.io
```

Expected: pull 성공. 실패하면 패키지가 private이라 권한 문제이므로, 패키지 설정에서 저장소를 연결(Package settings → Manage Actions access)한다.

---

## Task 5: EC2 최초 세팅 (수동 런북)

이 태스크는 사람이 EC2에 접속해 실행한다. 자동화 대상이 아니다. **구 컨테이너를 제거하는 파괴적 단계가 포함되므로 백업을 먼저 한다.**

**Files:**
- Create: `deploy/RUNBOOK.md` (아래 내용을 그대로 저장하고, 실행하며 결과를 기록한다)

- [ ] **Step 1: 현재 상태를 기록한다**

```bash
ssh -i <키> <user>@<host>
docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
docker compose version || echo "COMPOSE-V2-NONE"
df -h /
free -m
swapon --show || echo "SWAP-NONE"
```

결과를 `deploy/RUNBOOK.md`에 붙여 넣는다. 특히 **메모리**를 확인한다. 총 1GB 인스턴스라면 JVM 3개가 동시에 뜨지 못한다 — 그 경우 Step 2에서 스왑을 먼저 만든다.

- [ ] **Step 2: (메모리가 2GB 미만일 때만) 스왑을 만든다**

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -m
```

- [ ] **Step 3: docker compose v2가 없으면 설치한다**

Step 1에서 `COMPOSE-V2-NONE`이 나온 경우에만:

```bash
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
```

- [ ] **Step 4: 구 앱 이미지를 백업한다 (되돌릴 수 있게)**

```bash
OLD_IMAGE=$(docker inspect --format '{{.Config.Image}}' icuh_platform)
echo "$OLD_IMAGE"
docker tag "$OLD_IMAGE" icuh-platform:rollback
docker images | grep icuh-platform
```

`icuh-platform:rollback` 태그가 보이면 성공이다. 이 이미지가 있으면 언제든 구 앱을 8081로 되돌릴 수 있다.

- [ ] **Step 5: 배포 디렉터리를 만든다**

```bash
sudo mkdir -p /opt/icuh/logs/{public,admin,open}
sudo chown -R "$USER":"$USER" /opt/icuh
```

- [ ] **Step 6: `.env`를 작성한다**

저장소의 `deploy/.env.example`을 기준으로 실제 값을 채운다.

```bash
vi /opt/icuh/.env      # CHANGEME를 모두 실제 값으로
chmod 600 /opt/icuh/.env
grep -c CHANGEME /opt/icuh/.env    # 0 이어야 한다
```

- [ ] **Step 7: compose 파일을 배치하고 이미지를 미리 받아본다**

로컬에서:

```bash
scp -i <키> deploy/docker-compose.yml <user>@<host>:/opt/icuh/docker-compose.yml
```

EC2에서:

```bash
cd /opt/icuh
echo <GitHub PAT(read:packages)> | docker login ghcr.io -u <github-id> --password-stdin
IMAGE_TAG=latest docker compose pull
docker logout ghcr.io
```

Expected: 3개 이미지가 받아진다.

- [ ] **Step 8: 구 컨테이너를 내리고 신 앱을 올린다**

컨테이너 이름은 Step 1에서 확인한 실제 값을 쓴다. 아래는 이전 워크플로가 쓰던 이름이다.

```bash
docker stop icuh_platform && docker rm icuh_platform
cd /opt/icuh && IMAGE_TAG=latest docker compose up -d
docker compose ps
```

- [ ] **Step 9: 헬스체크로 확인한다**

```bash
curl -fsS localhost:8081/health && echo " public OK"
curl -fsS localhost:8082/health && echo " admin OK"
curl -fsS localhost:8083/health && echo " open OK"
```

셋 다 성공해야 한다. 실패하면 `docker compose logs <service>`로 원인을 본다. 대개 `.env`의 DB 접속 정보 문제다.

되돌리려면: `docker compose down && docker run -d --name icuh_platform -p 8081:8081 icuh-platform:rollback` (원래 실행 옵션은 `icuh-platform/.github/workflows/cicd.yml`의 Deploy 스텝 참고).

- [ ] **Step 10: 첫 성공 태그를 기록한다**

```bash
git rev-parse HEAD   # 로컬에서 현재 main의 SHA
```

EC2에서:

```bash
echo <그 SHA> > /opt/icuh/.last-good
```

- [ ] **Step 11: 보안그룹을 정리한다**

AWS 콘솔 → EC2 → 보안그룹 → 인바운드 규칙:

- 8081 — 이미 열려 있다 (구 앱이 쓰던 것). 유지.
- **8083 — 새로 추가한다.** 소스는 프론트가 접근할 범위로.
- **8082 — 추가하지 않는다.** admin-api는 인증이 없다.
- 22 — Actions가 배포 때 임시로 열고 닫으므로 상시 규칙은 두지 않는다(관리자 접속용 고정 IP 규칙은 별개로 유지해도 된다).

- [ ] **Step 12: 런북 결과를 커밋한다**

```bash
git add deploy/RUNBOOK.md
git commit -m "docs: EC2 최초 세팅 런북과 실행 결과 기록"
```

---

## Task 6: 배포 job과 롤백

**Files:**
- Modify: `.github/workflows/deploy.yml` (Task 4에서 만든 파일에 `deploy` job 추가)

**Interfaces:**
- Consumes: Task 4의 `build-and-push` job, Task 2의 compose 파일, Task 5에서 배치한 `/opt/icuh/.env`와 `.last-good`.
- Produces: 완성된 배포 파이프라인.

- [ ] **Step 1: GitHub Secrets를 정리한다**

Settings → Secrets and variables → Actions.

**추가/확인** — 이전 프로젝트(`icuh-platform`) 저장소에 있는 값을 그대로 옮긴다:

| 이름 | 값 |
|---|---|
| `SSH_EC2_KEY` | 배포용 pem 전문 |
| `SSH_EC2_USER` | 예: `ubuntu` |
| `SSH_EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| `AWS_ACCESS_KEY_ID` | 보안그룹 조작 권한 |
| `AWS_SECRET_ACCESS_KEY` | 〃 |
| `AWS_SG_ID` | 보안그룹 ID (`sg-...`) |

**추가하지 않는다**: Docker Hub 관련 3종. GHCR은 `GITHUB_TOKEN`으로 충분하다.

- [ ] **Step 2: `deploy` job을 추가한다**

`.github/workflows/deploy.yml` 끝에 붙인다.

```yaml
  deploy:
    needs: [ build-and-push ]
    # 롤백 실행(build 건너뜀)에서도 돌아야 하므로 always + 실패 판정을 명시한다.
    if: always() && (needs.build-and-push.result == 'success' || needs.build-and-push.result == 'skipped')
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: read

    steps:
      - uses: actions/checkout@v4

      - name: Resolve image tag
        id: tag
        run: |
          TAG="${{ inputs.image_tag }}"
          if [ -z "$TAG" ]; then TAG="${{ github.sha }}"; fi
          echo "value=$TAG" >> "$GITHUB_OUTPUT"
          echo "배포 대상 태그: $TAG"

      - name: Get GitHub Actions IP
        id: ip
        uses: haythem/public-ip@v1.2

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ap-northeast-2

      - name: Open SSH port for this runner
        run: |
          aws ec2 authorize-security-group-ingress \
            --group-id ${{ secrets.AWS_SG_ID }} \
            --protocol tcp --port 22 \
            --cidr ${{ steps.ip.outputs.ipv4 }}/32

      - name: Write SSH key
        run: |
          echo "${{ secrets.SSH_EC2_KEY }}" > deploy_key.pem
          chmod 400 deploy_key.pem

      - name: Sync compose file
        run: |
          scp -i deploy_key.pem -o StrictHostKeyChecking=no \
            deploy/docker-compose.yml \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }}:/opt/icuh/docker-compose.yml

      # 토큰을 stdin으로 넘긴다. 명령줄에 실리지 않으므로 EC2의 ps/히스토리에 남지 않는다.
      - name: Log in to GHCR on EC2
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | ssh -i deploy_key.pem -o StrictHostKeyChecking=no \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} \
            'docker login ghcr.io -u ${{ github.actor }} --password-stdin'

      - name: Pull and start
        env:
          IMAGE_TAG: ${{ steps.tag.outputs.value }}
        run: |
          ssh -i deploy_key.pem -o StrictHostKeyChecking=no \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} \
            "cd /opt/icuh && IMAGE_TAG=${IMAGE_TAG} docker compose pull && IMAGE_TAG=${IMAGE_TAG} docker compose up -d"

      - name: Health check
        id: health
        continue-on-error: true
        run: |
          ssh -i deploy_key.pem -o StrictHostKeyChecking=no \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} bash -s <<'REMOTE'
          set -u
          for i in $(seq 1 30); do
            if curl -fsS -m 3 localhost:8081/health >/dev/null \
               && curl -fsS -m 3 localhost:8082/health >/dev/null \
               && curl -fsS -m 3 localhost:8083/health >/dev/null; then
              echo "3개 앱 모두 정상"
              exit 0
            fi
            sleep 2
          done
          echo "헬스체크 실패 — 최근 로그"
          cd /opt/icuh && docker compose logs --tail 50
          exit 1
          REMOTE

      - name: Roll back on failed health check
        if: steps.health.outcome == 'failure'
        run: |
          ssh -i deploy_key.pem -o StrictHostKeyChecking=no \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} bash -s <<'REMOTE'
          set -eu
          cd /opt/icuh
          if [ ! -s .last-good ]; then
            echo ".last-good이 없어 자동 롤백을 못 한다. 수동 확인이 필요하다."
            exit 1
          fi
          PREV=$(cat .last-good)
          echo "직전 성공 태그로 되돌린다: $PREV"
          IMAGE_TAG="$PREV" docker compose up -d
          REMOTE
          echo "::error::헬스체크 실패로 롤백했다"
          exit 1

      - name: Record last-good tag
        if: steps.health.outcome == 'success'
        env:
          IMAGE_TAG: ${{ steps.tag.outputs.value }}
        run: |
          ssh -i deploy_key.pem -o StrictHostKeyChecking=no \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} \
            "echo ${IMAGE_TAG} > /opt/icuh/.last-good && docker logout ghcr.io"

      - name: Close SSH port
        if: always()
        run: |
          aws ec2 revoke-security-group-ingress \
            --group-id ${{ secrets.AWS_SG_ID }} \
            --protocol tcp --port 22 \
            --cidr ${{ steps.ip.outputs.ipv4 }}/32

      - name: Remove SSH key
        if: always()
        run: rm -f deploy_key.pem
```

- [ ] **Step 3: 문법을 확인한다**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/deploy.yml')); print('OK')"`
Expected: `OK`

- [ ] **Step 4: 커밋하고 main에 반영한다**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: EC2 배포와 헬스체크 실패 시 자동 롤백 추가"
```

PR을 만들어 머지한다.

- [ ] **Step 5: 첫 자동 배포를 확인한다**

Actions에서 `Deploy` 실행을 연다. 확인할 것:

1. `Open SSH port` → `Close SSH port`가 짝으로 실행됐는지
2. `Health check`가 `3개 앱 모두 정상`을 출력했는지
3. `Record last-good tag`가 돌았는지

EC2에서:

```bash
cat /opt/icuh/.last-good        # 방금 커밋 SHA
docker compose -f /opt/icuh/docker-compose.yml ps
```

- [ ] **Step 6: 롤백 리허설을 한다**

실제로 필요할 때 처음 눌러보면 늦다. 지금 한 번 돌려본다.

1. `git log --oneline -3`으로 **직전** 커밋 SHA를 확인한다 (전체 40자: `git rev-parse HEAD~1`)
2. Actions → Deploy → Run workflow → `image_tag`에 그 SHA를 넣고 실행
3. `build-and-push`가 skip되고 `deploy`만 도는지 확인
4. EC2에서 `docker compose ps`로 이미지 태그가 바뀌었는지 확인
5. 다시 최신 SHA로 한 번 더 실행해 원복한다

Expected: 4번에서 이미지 태그가 지정한 SHA로 바뀌어 있어야 한다. 바뀌지 않았다면 compose가 `IMAGE_TAG`를 못 읽은 것이므로 `Pull and start` 스텝의 따옴표를 확인한다.

- [ ] **Step 7: 이전 프로젝트 워크플로를 비활성화한다**

구 앱을 내렸으므로 `icuh-platform` 저장소의 `develop` 브랜치에 푸시가 들어가면 8081에 구 앱이 다시 뜬다. 그 저장소의 Actions → `icuh-platform CI/CD flow` → `⋯` → Disable workflow.

---

## 완료 기준

- [ ] PR을 열면 `CI`가 돌고 테스트가 게이트로 동작한다
- [ ] `main` 머지 시 GHCR에 SHA 태그로 3개 이미지가 올라간다
- [ ] EC2에 자동 배포되고 3개 `/health`가 200을 준다
- [ ] 헬스체크 실패 시 `.last-good`으로 되돌아간다 (리허설로 확인)
- [ ] 배포 중 열린 22번 포트가 항상 닫힌다
- [ ] EC2의 `ps`와 `~/.bash_history`에 DB 비밀번호가 남지 않는다
- [ ] 8082는 외부에서 닿지 않는다 (`curl <EC2 IP>:8082/health` 실패)

## 남은 과제 (이번 범위 밖)

- **admin-api 인증** — 지금은 네트워크 차단으로만 막고 있다. 근본 해결은 인증 도입이다.
- **batch 모듈** — Job이 생기면 cron + `docker run --rm`으로 별도 구성한다.
- **무중단 배포** — `compose up -d`는 컨테이너 교체 중 수 초의 단절이 있다. 필요해지면 앞단에 nginx를 두고 blue-green으로 확장한다.
- **SSM 전환** — 22번 포트를 아예 열지 않는 구성. 배포 명령이 이미 `compose up -d` 한 줄이라 전환 비용이 낮다.
