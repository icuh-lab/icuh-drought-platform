# CI/CD → EC2 배포 파이프라인 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `main`에 머지되면 3개 실행 모듈(public-api·admin-api·open-api)이 GHCR 이미지로 빌드되어 기존 EC2 한 대에 docker compose로 무인 배포되고, 실패 시 직전 성공 이미지로 자동 롤백되는 파이프라인을 만든다.

**Architecture:** CI에서 `./gradlew build`를 **한 번** 돌려 jar 3개를 만들고, Dockerfile은 런타임 스테이지만 두어 jar를 복사만 한다(이전 프로젝트는 컨테이너 안에서 gradle 빌드를 반복했다). 이미지는 커밋 SHA로 태깅해 GHCR에 올리고, 배포는 Actions가 보안그룹 22번을 한시적으로 열어 SSH로 들어가 `docker compose pull && up -d`만 실행한다. 앱 환경변수 20여 개는 EC2의 `/opt/icuh/.env.{public,admin,open}`(각 600)에 서비스별로 나뉘어 상주하고 Actions는 이미지 태그 하나만 넘긴다.

**Tech Stack:** GitHub Actions · GHCR · Docker / docker compose v2 · eclipse-temurin:17-jre · AWS EC2 + 보안그룹 · Gradle 8.13 / JDK 17

**Spec:** 별도 스펙 문서 없음. 승인된 설계를 아래 "설계 요약"에 옮겨 담았다. 실행자는 이 문서만 읽으면 된다.

## Global Constraints

- **JDK는 17로 고정한다.** 로컬/러너에 JDK 25가 잡히면 Gradle 8.13이 `Could not create task of type 'Test'`로 죽는다.
- **배포 대상은 3개 모듈뿐이다**: `public-api`(8081) · `admin-api`(8082) · `open-api`(8083). `batch`는 `IcuhDroughtBatchApplication.java` 하나뿐이고 Job 정의가 없어 이번 범위에서 제외한다.
- **이미지 이름**: `ghcr.io/icuh-lab/icuh-drought-platform/<module>` (소문자 고정).
- **이미지 태그**: `${{ github.sha }}` 전체 40자 + `latest` 병행. 배포는 항상 SHA 태그로 한다.
- **8082(admin-api)는 보안그룹에 열지 않는다.** admin-api는 인증이 전혀 없어 결재 승인·반려·병합 API가 무방비다. 관리자는 SSH 터널로 접근한다.
- **`SPRING_PROFILES_ACTIVE=prod`를 반드시 준다.** public-api의 기본 프로필은 `local`, open-api는 `dev`다. admin-api에는 `application-prod.yml`이 없지만 설정이 전부 환경변수 기반이라 동작에 문제없다.
- **운영 프로필 설정 파일은 git에 추적되어 있어야 한다.** CI는 체크아웃한 것만 빌드한다 — 작업 트리에만 있고 `.gitignore`된 설정 파일은 이미지 안에 존재하지 않는다. 값이 전부 `${...}` 환경변수 자리표시자로 옮겨진 뒤에도 무시 규칙만 남아 있던 것이 이번에 드러났다 (Task 1 Step 5 참고).
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
| 시크릿 전달 | `docker run -e` 인라인 | EC2 서비스별 `.env` 상주 |
| 배포 명령 | `stop → rm → run` | `compose pull && up -d` |
| 롤백 | 불가 | `.last-good` 태그로 자동/수동 |
| PR 동작 | PR에서도 EC2 배포 | PR은 검증만 |

EC2 파일 배치:

```
/opt/icuh/
├── docker-compose.yml   ← 저장소에서 관리, 배포마다 scp로 갱신
├── .env.public          ← public-api 환경변수. 600, git 미포함, 수동 관리
├── .env.admin           ← admin-api 환경변수. 600, git 미포함, 수동 관리
├── .env.open            ← open-api 환경변수. 600, git 미포함, 수동 관리
├── .last-good           ← 마지막 헬스체크 통과 SHA
└── logs/public/         ← public-api만 파일 로그를 남긴다. admin-api·open-api는 표준출력만 쓴다.
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

# 이 파이프라인이 만든 이미지임을 표시한다. EC2의 배포 후 정리(deploy.yml의
# docker image prune)가 이 라벨로 대상을 좁힌다 — 라벨이 없으면 나이만 보고 지우게 되어
# 런북이 백업해 둔 icuh-platform:rollback(구 앱 복구 수단)까지 함께 지워진다.
LABEL re.kr.icuh.project=drought-platform

COPY ${MODULE}/build/libs/*-SNAPSHOT.jar app.jar

# 힙 상한은 "컨테이너 메모리 한도의 70%"다. 한도가 걸려 있지 않으면 그 기준이 호스트 전체
# 메모리가 되어, EC2 한 대에 JVM 3개가 뜨는 이 구성에서는 210% 과다배정이 된다.
# 한도는 이 파일이 아니라 deploy/docker-compose.yml의 서비스별 mem_limit이 건다 —
# 그 값을 지우면 이 옵션은 보호 장치가 아니라 위험 요소가 된다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 5: 운영 프로필 설정 파일을 git 추적으로 되돌린다**

이 스텝이 없으면 아래 Step 6의 로컬 검증은 통과하지만 **CI가 만든 이미지는 기동하지 못한다.**
로컬 `docker build`는 작업 트리를 컨텍스트로 쓰므로 `.gitignore`된 파일까지 이미지에 들어가지만,
CI는 체크아웃한 것만 가지고 빌드하기 때문이다.

무시되고 있던 파일과 규칙:

| 파일 | 무시하던 규칙 |
|---|---|
| `public-api/src/main/resources/application-secret.yml` | `public-api/.gitignore` |
| `public-api/src/main/resources/application-prod.yml` | `public-api/.gitignore` |
| `admin-api/src/main/resources/application-private.yml` | `admin-api/.gitignore` |

(`open-api/src/main/resources/application-prod.yml`은 원래부터 추적되고 있어 영향이 없다.)

**커밋해도 되는 이유:** 세 파일에 남은 값은 전부 `${...}` 환경변수 자리표시자다. 리터럴은
`on-profile` 문자열과 `stack.auto: false`뿐이고, 실제 자격증명은 이미 EC2의 `.env.<name>`으로
옮겨졌다. 무시 규칙은 그 이관 이전의 잔재다. 커밋 전 세 파일에 리터럴 값이 없는지 다시 확인한다.

```bash
# public-api/.gitignore에서 두 줄, admin-api/.gitignore에서 한 줄을 지운다.
git add public-api/.gitignore admin-api/.gitignore
git add -f public-api/src/main/resources/application-secret.yml \
           public-api/src/main/resources/application-prod.yml \
           admin-api/src/main/resources/application-private.yml
git status --short   # 세 파일이 A로 스테이징돼 있어야 한다
```

검증(작업 트리가 아니라 **git이 가진 것**으로 빌드해야 의미가 있다):

```bash
git archive "$(git write-tree)" | tar -x -C /tmp/export && cd /tmp/export
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ./gradlew build
docker build --build-arg MODULE=public-api -t verify/public-api .
docker run --rm --env-file <deploy/.env.public.example 기반 파일> verify/public-api
```

Expected: 설정 누락(`Failed to configure a DataSource`, `Could not resolve placeholder
'spring.cloud.aws.credentials.access-key'`)이 아니라 **DB 도달 실패**(`Communications link failure`)로
바뀐다. DB가 없는 환경에서 기대할 수 있는 마지막 실패 지점이다.

`public-api`는 이 시점부터 `logback-prod.xml`이 적용된다(그전에는 `application-prod.yml`이 없어
`logging.yml`의 `logback-local.xml`로 폴백했다). 콘솔 appender가 없는 설정이라 표준출력에는 배너만
나오고 실제 로그는 `/root/log/spring/platform.log`로 간다 — `deploy/README.md`의 "로그" 절이 이때부터
사실이 된다.

- [ ] **Step 6: jar를 만들고 검증을 통과시킨다**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ./gradlew build
./scripts/verify-images.sh
```

Expected: `ALL OK`

- [ ] **Step 7: 로컬 이미지를 정리한다**

```bash
docker rmi icuh-local/public-api:verify icuh-local/admin-api:verify icuh-local/open-api:verify
```

- [ ] **Step 8: 커밋**

```bash
git add Dockerfile .dockerignore scripts/verify-images.sh
git commit -m "build: 3개 실행 모듈이 공유하는 런타임 Dockerfile 추가"
```

---

## Task 2: EC2 compose 구성과 환경변수 템플릿

**Files:**
- Create: `deploy/docker-compose.yml`
- Create: `deploy/.env.public.example`, `deploy/.env.admin.example`, `deploy/.env.open.example`
- Create: `deploy/README.md`

**Interfaces:**
- Consumes: Task 1의 이미지 이름 규칙 `ghcr.io/icuh-lab/icuh-drought-platform/<module>`.
- Produces: EC2 `/opt/icuh/docker-compose.yml`이 될 파일. 서비스 이름은 `public-api`·`admin-api`·`open-api`. 외부에서 `IMAGE_TAG` 환경변수 하나만 주입받는다. Task 5의 런북과 Task 6의 배포 job이 이 파일을 쓴다.

- [ ] **Step 1: 렌더링 검증을 먼저 시도한다 (실패하는 테스트)**

Run:
```bash
IMAGE_TAG=testsha docker compose -f deploy/docker-compose.yml config
```
Expected: FAIL — `no such file or directory`.

- [ ] **Step 2: 서비스별 `deploy/.env.<name>.example`을 만든다**

실제 값이 아닌 자리표시자만 넣는다. 이 파일들은 커밋되고, 실제 `.env.<name>`은 EC2에만 둔다.
`.env` 하나를 세 컨테이너가 공유하면 admin-api 전용 시크릿이 인터넷에 노출된 public-api·open-api 프로세스
환경에도 실려 들어가므로, 서비스별로 분리한다. `SPRING_PROFILES_ACTIVE=prod`는 각 컨테이너가 모두 필요로
하므로 세 파일 모두에 넣는다.

`deploy/.env.public.example`:

```bash
# EC2 /opt/icuh/.env.public 템플릿. 실제 값을 채워 600 권한으로 두고 절대 커밋하지 않는다.
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
```

`deploy/.env.admin.example`:

```bash
# EC2 /opt/icuh/.env.admin 템플릿. 실제 값을 채워 600 권한으로 두고 절대 커밋하지 않는다.
# 항목 근거: RUNTIME_CONFIG.md

SPRING_PROFILES_ACTIVE=prod

# --- admin-api (8082, 보안그룹 미개방) ---
ADMIN_DB_URL=jdbc:mysql://CHANGEME:3306/ACTUAL_DRGHT
ADMIN_DB_USERNAME=CHANGEME
ADMIN_DB_PASSWORD=CHANGEME
ADMIN_CORS_ALLOWED_ORIGINS=http://localhost:3000
ADMIN_S3_BUCKET_NAME=CHANGEME
ADMIN_AWS_REGION=ap-northeast-2
ADMIN_AWS_ACCESS_KEY_ID=CHANGEME
ADMIN_AWS_SECRET_ACCESS_KEY=CHANGEME
```

`deploy/.env.open.example`:

```bash
# EC2 /opt/icuh/.env.open 템플릿. 실제 값을 채워 600 권한으로 두고 절대 커밋하지 않는다.
# 항목 근거: RUNTIME_CONFIG.md

SPRING_PROFILES_ACTIVE=prod

# --- open-api (8083) ---
OPEN_API_DB_URL=jdbc:mysql://CHANGEME:3306/ACTUAL_DRGHT
OPEN_API_DB_USERNAME=CHANGEME
OPEN_API_DB_PASSWORD=CHANGEME
OPEN_API_CORS_ALLOWED_ORIGINS=https://CHANGEME
```

저장소 루트의 `.gitignore`에 `deploy/.env*`(템플릿 `.example` 제외)를 추가해, 실수로 실제 값을 커밋하는 경로를
한 겹 더 막는다.

- [ ] **Step 3: `deploy/docker-compose.yml`을 만든다**

```yaml
# EC2 /opt/icuh/docker-compose.yml 로 배치된다.
# IMAGE_TAG만 배포 시점에 셸 환경변수로 주입받고, 앱 환경변수는 같은 디렉터리의 서비스별 .env.<name>에서 읽는다.
# .env를 세 컨테이너가 공유하면 admin-api 전용 시크릿이 인터넷에 노출된 public-api/open-api에도 실린다.
# 서비스별로 분리해 그 경로를 막는다.
#
# mem_limit이 왜 반드시 있어야 하나: Dockerfile의 -XX:MaxRAMPercentage=70은 "컨테이너 한도의 70%"를
# 힙으로 잡는데, 한도가 없으면 JVM은 호스트 전체 메모리를 자기 몫으로 본다 — 세 컨테이너가 각각
# 호스트의 70%를 잡아 210% 과다배정이 되고, 먼저 힙을 늘린 프로세스가 나머지를 OOM으로 밀어낸다.
# 아래 값은 인스턴스 크기를 모른 채 정한 보수적인 기본값이다(합계 1792m). **실제 인스턴스 메모리에
# 맞춰 반드시 조정한다** — 런북 Step 1의 `free -m`이 그 값을 재는 지점이고, 총합이 물리 메모리에서
# OS/도커 몫(넉넉히 512m)을 뺀 값을 넘지 않아야 한다.

services:
  public-api:
    image: ghcr.io/icuh-lab/icuh-drought-platform/public-api:${IMAGE_TAG}
    container_name: icuh-public-api
    env_file: .env.public
    # 셋 중 유일하게 외부 트래픽과 S3 멀티파트 업로드를 받는다 — 여유를 더 준다.
    mem_limit: 768m
    ports:
      - "8081:8081"
    volumes:
      - ./logs/public:/root/log/spring
    restart: unless-stopped

  admin-api:
    image: ghcr.io/icuh-lab/icuh-drought-platform/admin-api:${IMAGE_TAG}
    container_name: icuh-admin-api
    env_file: .env.admin
    mem_limit: 512m
    # 인증이 없는 앱이다. 호스트 루프백에만 바인딩해 외부에서 직접 닿지 못하게 한다.
    ports:
      - "127.0.0.1:8082:8082"
    restart: unless-stopped

  open-api:
    image: ghcr.io/icuh-lab/icuh-drought-platform/open-api:${IMAGE_TAG}
    container_name: icuh-open-api
    env_file: .env.open
    mem_limit: 512m
    ports:
      - "8083:8083"
    restart: unless-stopped
```

admin-api는 `127.0.0.1:8082:8082`로 묶는다. 보안그룹 설정과 무관하게 호스트 밖에서 닿지 않으므로 방어가 두 겹이 된다.

`mem_limit`은 Dockerfile의 `-XX:MaxRAMPercentage=70`과 짝을 이룬다. 한쪽만 있으면 의미가 없다 —
한도 없이 백분율만 있으면 세 JVM이 각각 호스트 전체의 70%를 힙 상한으로 잡는다. 값 자체는 인스턴스
크기를 확인하기 전의 보수적인 기본값이므로, 런북 Step 1의 `free -m` 결과에 맞춰 조정한다.

`public-api`만 `volumes`가 있다. `application-prod.yml`이 `logback-prod.xml`을 통해 `${user.home}/log/spring`에
파일 로그를 쓰도록 설정된 것은 public-api뿐이고, admin-api·open-api는 별도 logback 설정이 없어 표준출력으로만
로그를 낸다. 없는 파일 로그 경로에 빈 볼륨을 걸어두면 실제로는 비어 있는데 운영자에게는 "로그가 쌓이고 있다"는
잘못된 인상을 준다.

- [ ] **Step 4: 렌더링을 확인한다**

렌더링에는 각 서비스가 참조하는 `.env.public`/`.env.admin`/`.env.open`이 실제로 있어야 한다(`env_file:`은 파일
존재 여부를 그 자리에서 검증한다). 커밋되는 건 `.example` 뿐이므로, 검증 때만 임시로 복사했다가 지운다.

```bash
cp deploy/.env.public.example deploy/.env.public
cp deploy/.env.admin.example deploy/.env.admin
cp deploy/.env.open.example deploy/.env.open
IMAGE_TAG=testsha docker compose -f deploy/docker-compose.yml config | grep -E "image:|published:"
rm deploy/.env.public deploy/.env.admin deploy/.env.open
```
Expected: 3개 이미지가 `:testsha` 태그로, 포트가 `8081` / `127.0.0.1:8082` / `8083`으로 렌더링된다. 임시 파일을
지운 뒤 `git status --short`로 실제 `.env.*`가 남지 않았는지 확인한다.

- [ ] **Step 5: `deploy/README.md`를 만든다**

```markdown
# 배포 구성

EC2 한 대에 public-api(8081) · admin-api(8082) · open-api(8083) 세 컨테이너를 올린다.

## 파일

| 파일 | 위치 | 관리 |
|---|---|---|
| `docker-compose.yml` | 저장소 → 배포마다 EC2 `/opt/icuh/`로 scp | git |
| `.env.public` | EC2 `/opt/icuh/.env.public` (600) | 수동. `.env.public.example` 참고 |
| `.env.admin` | EC2 `/opt/icuh/.env.admin` (600) | 수동. `.env.admin.example` 참고 |
| `.env.open` | EC2 `/opt/icuh/.env.open` (600) | 수동. `.env.open.example` 참고 |
| `.last-good` | EC2 `/opt/icuh/.last-good` | 배포 워크플로가 갱신 |

세 서비스가 `.env` 하나를 공유하면 admin-api 전용 시크릿(DB 비밀번호 등)이 인터넷에 노출된 public-api·open-api
컨테이너의 프로세스 환경에도 실린다. 그래서 서비스마다 별도 env 파일을 쓴다.

## 로그

`public-api`만 `./logs/public`에 파일 로그를 남긴다(`application-prod.yml`의
`logging.config`가 `logback-prod.xml`을 가리키고, 그 설정이 `${user.home}/log/spring`(컨테이너 안
`/root/log/spring`)에 쓴다. compose가 이 경로를 `./logs/public`에 마운트한다).

**`logback-prod.xml`에는 콘솔 appender가 없다.** 그래서 prod로 뜬 `public-api`는 배너 이후 표준출력에
아무것도 내지 않는다 — `docker compose logs public-api`는 사실상 비어 있고, 배포 워크플로가 헬스체크
실패 시 찍는 `docker compose logs --tail 200`에도 `public-api` 로그는 나오지 않는다.
`public-api`의 기동 실패 원인은 호스트의 `/opt/icuh/logs/public/platform.log`에서 본다.

```bash
tail -100 /opt/icuh/logs/public/platform.log
```

`admin-api`·`open-api`는 별도 logback 설정이 없어 표준출력으로만 로그를 낸다 —
`docker logs icuh-admin-api` / `docker logs icuh-open-api`로 확인한다.

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

## 메모리

`docker-compose.yml`이 서비스마다 `mem_limit`을 건다(public 768m / admin 512m / open 512m).
Dockerfile의 `-XX:MaxRAMPercentage=70`이 힙을 잡는 기준이 이 한도라서, 한도를 지우면 JVM 3개가
각각 호스트 전체 메모리의 70%를 자기 몫으로 잡는다. 값 자체는 보수적인 기본값이니 실제 인스턴스
메모리(`free -m`)에 맞춰 조정한다.

## 롤백

Actions → deploy 워크플로 → Run workflow → `image_tag`에 되돌릴 커밋 SHA 입력.
`image_tag`는 **40자 커밋 SHA**여야 한다(`latest` 같은 값이나 짧은 SHA는 거부된다).
`image_tag`를 비운 채 실행하는 것은 `main`에서만 허용된다 — 다른 브랜치에서 비운 채 실행하면
그 브랜치가 운영에 올라가는 것을 막기 위해 첫 스텝에서 실패한다.
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
      - uses: actions/checkout@v7

      # Gradle 8.13은 JDK 25에서 기동하지 못한다. 17로 고정한다.
      - name: Set up JDK 17
        uses: actions/setup-java@v6
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6

      # 이전 파이프라인은 -x test 였다. 여기가 실제 게이트다.
      - name: Build and test
        run: ./gradlew build --console=plain

      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v7
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
      - uses: actions/checkout@v7

      - name: Set up JDK 17
        uses: actions/setup-java@v6
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6

      # 한 번만 돌린다. 3개 모듈의 jar가 모두 나온다.
      - name: Build and test
        run: ./gradlew build --console=plain

      - name: Log in to GHCR
        uses: docker/login-action@v4
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

**실행 순서 — 이 런북은 언제 도는가.** 런북 맨 앞에도 같은 내용을 적는다. 순서를 모르면 정상적인
실패를 파이프라인 고장으로 오해한다.

1. 브랜치를 `main`에 머지한다.
2. 그 push로 `Deploy`가 자동 실행된다. **첫 실행은 실패하는 것이 정상이다** — `build-and-push`는
   성공해 GHCR에 이미지를 올리지만(런북 Step 8이 받아 갈 이미지가 이때 생긴다), `deploy` job은
   EC2에 `/opt/icuh`가 없어 `Sync compose file`에서 멈춘다. 안전한 실패다: 컨테이너를 건드리지
   않았고, `Pull and start`/`Health check`가 실행되지 않아 롤백 스텝은 조건이 거짓이며(`.last-good`도
   아직 없다), `Close SSH port`가 22번 규칙을 되돌린다.
3. 이 런북을 Step 1~15까지 실행한다.
4. 끝나면 Actions → Deploy → Run workflow(`main`, `image_tag` 비움)로 다시 돌린다. 이번에는 끝까지
   통과해야 한다 — 파이프라인 전체가 도는지 확인하는 첫 실행이다.

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

`free -m`의 총 메모리는 compose의 `mem_limit`(합계 1792m)을 확정하는 근거이기도 하다. 총 메모리에서
OS·도커 몫(넉넉히 512m)을 뺀 값이 1792m보다 작으면 로컬 체크아웃에서 `deploy/docker-compose.yml`의
`mem_limit`을 줄인다. 한도를 지우는 것은 답이 아니다 — Dockerfile의 `-XX:MaxRAMPercentage=70`이
한도 없을 때 호스트 전체를 기준으로 삼는다.

**여기서 커밋하지 않는다.** `main`에 푸시되면 아직 세팅이 끝나지 않은 호스트로 `Deploy`가 나가서,
"실행 순서"가 약속한 "실패하는 것은 첫 실행 하나뿐"이 깨진다. 고친 파일은 Step 8의 `scp`가 작업
트리에서 그대로 올려 주므로 런북 동안 문제없이 쓰이고, 커밋은 Step 15에서 한 번에 한다.

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

- [ ] **Step 4: 구 앱 이미지와 실행 설정을 백업한다 (되돌릴 수 있게)**

백업 파일을 쓸 자리가 있어야 하므로, Step 5가 만드는 `/opt/icuh` 중 최상위 디렉터리만 여기서 먼저
만든다(로그용 하위 디렉터리와 전체 소유권 정리는 Step 5에서 마저 한다):

```bash
sudo mkdir -p /opt/icuh
sudo chown -R "$USER":"$USER" /opt/icuh
```

컨테이너 이름은 아래에서 한 번만 적는다. 나머지 명령은 모두 이 값(`$OLD`)을 그대로 쓴다 — 같은
이름을 두 군데 이상에 따로 적으면 한쪽만 고치고 다른 쪽을 놓치는 사고가 나기 때문에, 고칠 곳을
하나로 줄인다. `OLD`는 이 셸 세션에만 남는 변수라서, 접속이 끊겼다가 다시 붙었다면 Step 10 전에
이 블록부터 다시 실행해야 한다.

```bash
# Step 1에서 확인한 실제 컨테이너 이름으로 바꾼다. 아래 명령은 모두 이 값을 쓴다.
OLD=icuh_platform

OLD_IMAGE=$(docker inspect --format '{{.Config.Image}}' "$OLD")
echo "$OLD_IMAGE"
docker tag "$OLD_IMAGE" icuh-platform:rollback

docker inspect "$OLD" > /opt/icuh/old-container-inspect.json
docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$OLD" > /opt/icuh/old-container-env.txt
chmod 600 /opt/icuh/old-container-inspect.json /opt/icuh/old-container-env.txt
```

이미지 태그만으로는 부족하다 — Step 10의 `docker rm`이 컨테이너의 실행 설정(환경변수 십여 개, 포트
매핑, 볼륨 마운트)까지 함께 지운다. 그래서 `docker inspect` 전체 출력과 환경변수 목록도 파일로 남긴다.

`old-container-env.txt`에는 구 앱의 DB 비밀번호와 AWS 시크릿 키가 평문으로 들어 있다. `chmod 600`으로
권한을 좁히고, 새 배포가 충분히 안정화되어 롤백 가능성이 없다고 판단되면 두 파일을 삭제한다.

줄 수만 세는 확인은 속는다 — `$OLD`에 존재하지 않는 컨테이너 이름을 넣으면 `docker inspect`는
표준출력에 `[]` 한 줄만 내보내고 종료 코드는 0이 아니지만, 그 한 줄이 그대로 파일에 리다이렉트돼
"0줄보다 많다"는 확인은 통과해 버린다. 그래서 줄 수 대신 파일 내용을 본다:

```bash
grep -q '"Id"' /opt/icuh/old-container-inspect.json \
  && echo "설정 백업 OK" \
  || echo "실패: '$OLD' 컨테이너를 찾지 못했다. Step 1 출력에서 실제 이름을 확인하고 다시 실행한다."
test -s /opt/icuh/old-container-env.txt \
  && echo "환경변수 백업 OK ($(wc -l < /opt/icuh/old-container-env.txt)줄)" \
  || echo "실패: 환경변수를 받지 못했다."
docker images | grep icuh-platform
```

`설정 백업 OK`·`환경변수 백업 OK`가 둘 다 나오고 `icuh-platform:rollback`이 보이면 성공이다. 이
이미지와 두 파일이 있으면 언제든 구 앱을 8081로 되돌릴 수 있다.

이 이미지는 배포 워크플로의 `docker image prune`에 지워지지 않는다 — 정리 대상이
`label=re.kr.icuh.project=drought-platform`(Task 1의 Dockerfile `LABEL`)으로 한정돼 있기 때문이다.
라벨 필터가 없다면 Step 10의 `docker rm` 직후 참조가 끊기고 생성 시각도 336h를 넘긴 이 이미지가
첫 성공 배포에서 곧바로 지워졌을 것이다.

- [ ] **Step 5: 배포 디렉터리를 만든다**

```bash
sudo mkdir -p /opt/icuh/logs/public
sudo chown -R "$USER":"$USER" /opt/icuh
```

- [ ] **Step 6: 서비스별 `.env.<name>`을 작성한다**

저장소의 `deploy/.env.public.example` · `deploy/.env.admin.example` · `deploy/.env.open.example`을 기준으로
실제 값을 채운다. 세 파일로 나누는 이유는 한 `.env`를 세 컨테이너가 공유하면 admin-api 전용 시크릿이
인터넷에 노출된 public-api·open-api 프로세스 환경에도 실리기 때문이다.

```bash
vi /opt/icuh/.env.public   # CHANGEME를 모두 실제 값으로
vi /opt/icuh/.env.admin
vi /opt/icuh/.env.open
chmod 600 /opt/icuh/.env.public /opt/icuh/.env.admin /opt/icuh/.env.open
grep -c CHANGEME /opt/icuh/.env.public /opt/icuh/.env.admin /opt/icuh/.env.open   # 모두 0 이어야 한다
```

- [ ] **Step 7: 배포할 커밋 SHA를 한 번만 정한다**

Step 8(pull) · Step 10(기동) · Step 12(`.last-good` 기록)가 **같은 값**을 써야 한다. `latest`로
띄우면서 `.last-good`에는 SHA를 적으면, 기록된 롤백 포인터와 실제로 도는 이미지가 처음부터
어긋난다(Global Constraints의 "배포는 항상 SHA 태그로 한다"에도 어긋난다).

로컬에서 — "실행 순서" 2번의 `build-and-push`가 성공시킨 그 커밋이다:

```bash
git fetch origin main
git rev-parse origin/main
```

EC2에서:

```bash
TAG=<그 40자 SHA>
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] && echo "TAG OK: $TAG" || echo "실패: 40자 커밋 SHA가 아니다."
```

`TAG`는 `OLD`와 마찬가지로 이 셸 세션에만 남는다. 재접속했다면 Step 8·10·12 전에 다시 설정한다.

- [ ] **Step 8: compose 파일을 배치하고 이미지를 미리 받아본다**

로컬에서 — 커밋 여부와 무관하게 작업 트리의 파일이 그대로 올라간다(Step 1에서 `mem_limit`을 줄였다면
그 값이 여기서 반영된다):

```bash
scp -i <키> deploy/docker-compose.yml <user>@<host>:/opt/icuh/docker-compose.yml
```

EC2에서:

```bash
cd /opt/icuh
echo <GitHub PAT(read:packages)> | docker login ghcr.io -u <github-id> --password-stdin
: "${TAG:?Step 7의 TAG가 설정돼 있지 않다.}" \
  && IMAGE_TAG="$TAG" docker compose pull
```

Expected: 3개 이미지가 받아진다. **여기서는 로그아웃하지 않는다** — Step 12의 `.last-good` 검증
pull이 같은 로그인 세션을 쓴다. 로그아웃은 Step 12 끝에서 한다.

- [ ] **Step 9: 구 프로젝트의 배포 워크플로를 비활성화한다**

**Step 10(파괴적 단계)보다 먼저 한다.** 구 앱은 `icuh-platform` 저장소의 워크플로가 `develop` push마다
EC2에 재배포한다. 그대로 두면 Step 10에서 구 컨테이너를 지운 뒤 누군가 `develop`에 push하는 순간
구 앱이 8081에 되살아나 새 `public-api`와 충돌한다.

GitHub → `icuh-platform` → Actions → EC2로 배포하는 워크플로 → `⋯` → **Disable workflow**.

- [ ] **Step 10: 구 컨테이너를 내리고 신 앱을 올린다**

컨테이너 이름은 Step 4의 `$OLD`, 이미지 태그는 Step 7의 `$TAG`를 그대로 쓴다 — 여기서 새로 적지
않는다(같은 값을 두 곳에 따로 적으면 한쪽만 고치는 사고가 날 수 있어서다. `icuh_platform`은 이전
워크플로가 쓰던 이름을 그대로 옮겨 적은 추측값이고, 실제 값은 Step 4에서 확정한다).

아래는 한 덩어리로 붙여넣는 명령 하나다. 가드부터 재시작 확인까지 전부 `&&`로 묶여 있어, 중간 어느
지점이 실패하든 그 뒤는 전혀 실행되지 않고 곧바로 `중단됨` 메시지가 출력된다 — 블록을 둘로 나눠서
사이에 확인만 끼워 넣으면 그 경계에서 제어 흐름이 새어 나가기 때문에, 하나의 체인으로만 안전하다.
`OLD`나 `TAG`가 이 셸 세션에 남아 있지 않으면(재접속 등으로 사라졌으면) 맨 앞의 가드가 실패한다.
가드가 셸 자체를 멈추는 것은 아니다 — `&&`는 대화형이든 비대화형이든 동일하게 동작해서, 가드가
실패한 순간 그 뒤에 연결된 `docker stop`·`docker rm`·재시작 확인·`docker compose up`·
`docker compose ps`가 전부 건너뛰어질 뿐이다. 체인 중간의 `! docker ps -a --filter ... | grep -q .`는
구 컨테이너가 실제로 사라졌는지 재확인하는 지점이다 — 아직 남아 있으면 여기서 실패해 뒤의
`docker compose up -d`로 넘어가지 않는다.

```bash
: "${OLD:?Step 4의 OLD가 설정돼 있지 않다. 재접속했다면 Step 4를 다시 실행한다.}" \
  && : "${TAG:?Step 7의 TAG가 설정돼 있지 않다. 재접속했다면 Step 7을 다시 실행한다.}" \
  && docker stop "$OLD" \
  && docker rm "$OLD" \
  && ! docker ps -a --filter "name=^/${OLD}$" --format '{{.Names}}' | grep -q . \
  && cd /opt/icuh \
  && IMAGE_TAG="$TAG" docker compose up -d --remove-orphans \
  && docker compose ps \
  || echo "중단됨 — 위 출력을 확인한다. OLD/TAG 미설정, 구 컨테이너 제거 실패, compose 기동 실패 중 하나다."
```

`중단됨`이 보이면, 그 직전까지 화면에 실제로 찍힌 것이 이 체인이 실행한 마지막 지점이다 — 그 뒤로는
아무 것도 실행되지 않았다.

- [ ] **Step 11: 헬스체크로 확인한다**

```bash
curl -fsS localhost:8081/health && echo " public OK"
curl -fsS localhost:8082/health && echo " admin OK"
curl -fsS localhost:8083/health && echo " open OK"
```

셋 다 성공해야 한다. 실패하면 `docker compose logs <service>`로 원인을 본다. 대개 해당 서비스의
`.env.<name>`의 DB 접속 정보 문제다. 단 **`public-api`는 `docker compose logs`에 아무것도 남기지
않는다**(`logback-prod.xml`에 콘솔 appender가 없다) — `tail -100 /opt/icuh/logs/public/platform.log`를
본다.

되돌리려면: `docker compose down && docker run -d --name icuh_platform -p 8081:8081 icuh-platform:rollback`.
이 명령은 최소 기동만 한다 — 원래 포트/환경변수/볼륨 옵션은 Step 4에서 남긴
`/opt/icuh/old-container-inspect.json` · `/opt/icuh/old-container-env.txt`를 보고 반영한다. 두 파일이
없다면 참고용으로 `public-api/.github/workflows/cicd.yml`의 Deploy 스텝을 대신 본다(브리프가 원래
가리키던 `icuh-platform/.github/workflows/cicd.yml`은 이 저장소에 없는 경로다).

- [ ] **Step 12: 첫 성공 태그를 기록하고, 그 태그가 실재하는 이미지인지 확인한다**

`.last-good`은 워크플로가 자동 롤백할 때 읽는 유일한 파일이다. 여기 적힌 태그로 이미지를 실제로 받을
수 없으면 자동 롤백은 그 순간 실패한다 — 그래서 기록하고 끝내지 않고 곧바로 pull로 검증한다.

```bash
cd /opt/icuh
: "${TAG:?Step 7의 TAG가 설정돼 있지 않다.}" \
  && echo "$TAG" > /opt/icuh/.last-good \
  && grep -Eq '^[0-9a-f]{40}$' /opt/icuh/.last-good \
  && IMAGE_TAG=$(cat /opt/icuh/.last-good) docker compose pull \
  && echo ".last-good 검증 OK — 이 태그로 자동 롤백이 가능하다" \
  || echo "실패: .last-good이 40자 SHA가 아니거나, 그 태그의 이미지를 받을 수 없다."
docker logout ghcr.io   # Step 8에서 미뤄 둔 로그아웃
```

`.last-good 검증 OK`가 나와야 다음으로 넘어간다.

- [ ] **Step 13: 보안그룹을 정리한다**

AWS 콘솔 → EC2 → 보안그룹 → 인바운드 규칙:

- 8081 — 이미 열려 있다 (구 앱이 쓰던 것). 유지.
- **8083 — 새로 추가한다.** 소스는 프론트가 접근할 범위로.
- **8082 — 추가하지 않는다.** admin-api는 인증이 없다.
- 22 — Actions가 배포 때 임시로 열고 닫으므로 상시 규칙은 두지 않는다(관리자 접속용 고정 IP 규칙은 별개로 유지해도 된다).

이 보안그룹의 ID(`sg-...`)를 적어 둔다 — Step 14의 `AWS_SG_ID`가 정확히 이 값이어야 한다.

- [ ] **Step 14: 이 저장소의 GitHub Secrets를 확인한다**

Task 6 Step 1과 같은 여섯 개다. 런북에도 넣는 이유: 운영자가 실제로 따라 읽는 문서는 런북 하나이고,
이 여섯 개가 틀리면 다음 스텝의 수동 실행이 실패하거나 **엉뚱한 호스트에 배포한다.**

| 이름 | 값 |
|---|---|
| `SSH_EC2_KEY` | 이 EC2 접속용 개인키 전문 |
| `SSH_EC2_USER` | Step 1에서 접속에 쓴 `<user>` |
| `SSH_EC2_HOST` | Step 1에서 접속한 그 `<host>` |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | 보안그룹 조작 권한 |
| `AWS_SG_ID` | Step 13에서 적어 둔 보안그룹 ID |

**주의:** 이 여섯 이름은 모듈별 구 워크플로(`*/.github/workflows/cicd.yml`)가 쓰던 이름과 같다.
이 저장소에 이미 값이 들어 있을 수 있고, 그 값이 **이전 프로젝트의 호스트나 보안그룹을 가리킬 수
있다.** 게다가 GitHub Secrets는 저장 후 값을 다시 읽을 수 없어 눈으로 대조할 방법이 없다 — 그래서
"확인"은 **여섯 개를 지금 아는 값으로 다시 저장하는 것**이다. 특히 `SSH_EC2_HOST`와 `AWS_SG_ID`는
반드시 덮어쓴다.

- [ ] **Step 15: 런북 결과를 커밋하고 Deploy를 수동 실행한다**

**런북에서 커밋하는 지점은 여기 하나뿐이다.** Step 1의 `mem_limit` 조정도 여기서 함께 커밋한다.

```bash
git add deploy/RUNBOOK.md
git add deploy/docker-compose.yml   # Step 1에서 mem_limit을 조정했을 때만
git commit -m "docs: EC2 최초 세팅 런북과 실행 결과 기록"
```

그리고 "실행 순서" 4번 — Actions → Deploy → Run workflow(`main`, `image_tag` 비움). 이 실행이 끝까지
통과해야 파이프라인 전체가 동작한다는 것이 확인된다.

---

## Task 6: 배포 job과 롤백

**Files:**
- Modify: `.github/workflows/deploy.yml` (Task 4에서 만든 파일에 `deploy` job 추가)

**Interfaces:**
- Consumes: Task 4의 `build-and-push` job, Task 2의 compose 파일, Task 5에서 배치한 `/opt/icuh/.env.{public,admin,open}`와 `.last-good`.
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

**"확인"은 눈으로 보는 것이 아니다.** 이 여섯 이름은 모듈별 구 워크플로가 쓰던 이름과 같아서 이
저장소에 이미 값이 들어 있을 수 있고, 그 값이 이전 프로젝트의 호스트/보안그룹을 가리킬 수 있다.
GitHub Secrets는 저장 후 값을 다시 읽을 수 없으므로 여섯 개를 모두 지금 아는 값으로 다시 저장한다.
같은 내용을 런북 Step 14에도 넣었다 — 운영자가 실제로 따라 읽는 문서는 런북이기 때문이다.

- [ ] **Step 2: `deploy` job을 추가한다**

`.github/workflows/deploy.yml`에 두 군데를 손본다: `concurrency:` 블록은 `on:` 다음, `jobs:` 앞에 넣고, `deploy` job은 파일 끝에 붙인다.

```yaml
# 연이은 실행이 같은 EC2 보안그룹 규칙을 두고 경쟁하지 않도록 전체 워크플로를 직렬화한다.
# cancel-in-progress는 켜지 않는다 — Close SSH port는 always()라 취소돼도 22번
# 포트 자체는 대개 닫힌다. 진짜 문제는 deploy job의 !cancelled()다: 이미 실행
# 중인 job은 취소돼도 그 조건을 다시 평가하지 않으므로, 한창 배포/롤백 중인
# job이 강제로 끊길 수 있다 — !cancelled()가 이를 막아 준다고 오해하면 안 된다.
concurrency:
  group: deploy-ec2
  cancel-in-progress: false
```

```yaml
  deploy:
    needs: [ build-and-push ]
    # 롤백 실행(build 건너뜀)에서도 돌아야 하므로 always 대신 !cancelled()를 쓴다.
    # always()는 취소 상태까지 무시해 build-and-push 성공 직후 취소된 실행에서도
    # 배포를 강행해 버린다.
    if: ${{ !cancelled() && (needs.build-and-push.result == 'success' || needs.build-and-push.result == 'skipped') }}
    runs-on: ubuntu-latest
    # timeout-minutes 산정 근거: 설정(체크아웃~SSH 준비, 각 스텝 수십 초) +
    # pull/up(수 분) + 헬스체크 루프(데드라인 240초 상한) + 롤백 pull/up(수 분) +
    # 롤백 헬스체크 루프(데드라인 240초 상한)를 다 더해도 20분 안팎이다.
    # 25분은 그 위에 여유를 둔 값이다 — 루프의 deadline(현재 240초)을 바꾸면
    # 이 숫자도 다시 계산해야 한다.
    timeout-minutes: 25
    permissions:
      contents: read
      packages: read

    steps:
      # 첫 스텝이다. AWS 자격증명·보안그룹·SSH 키를 건드리기 전에 막는다.
      # workflow_dispatch는 어떤 브랜치에서도 실행할 수 있어서, image_tag를 비운 채
      # 기능 브랜치에서 실행하면 그 브랜치를 빌드해 운영 EC2에 올려 버린다.
      # main이 아닌 ref에서는 되돌릴 대상 SHA(image_tag)를 명시했을 때만 허용한다.
      # push 트리거는 main 한정이라 늘 통과한다.
      - name: Guard against deploying a non-main ref
        env:
          REF: ${{ github.ref }}
          RAW_TAG: ${{ inputs.image_tag }}
        run: |
          if [ "$REF" != "refs/heads/main" ] && [ -z "$RAW_TAG" ]; then
            echo "::error::main이 아닌 ref($REF)에서 image_tag 없이 배포할 수 없다. 롤백이라면 되돌릴 커밋 SHA를 image_tag에 넣고, 배포라면 main에서 실행한다."
            exit 1
          fi

      # 태그는 아래에서 원격 셸 명령 문자열에 그대로 끼워 넣는다. 40자 커밋 SHA만
      # 허용해 오타 배포와 명령 주입을 함께 막는다.
      # checkout **앞에** 둔다. checkout이 같은 값을 ref로 받으므로, 뒤에 두면 잘못된
      # 태그가 여기까지 오지 못하고 checkout 안에서 git 오류로 죽어 아래 ::error:: 메시지가
      # 끝내 보이지 않는다. 이 스텝은 inputs.image_tag만 읽어서 저장소가 필요 없다.
      - name: Resolve image tag
        id: tag
        env:
          RAW_TAG: ${{ inputs.image_tag }}
        run: |
          TAG="$RAW_TAG"
          if [ -z "$TAG" ]; then TAG="${{ github.sha }}"; fi
          [[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo "::error::image_tag는 40자 커밋 SHA여야 한다"; exit 1; }
          echo "value=$TAG" >> "$GITHUB_OUTPUT"
          echo "배포 대상 태그: $TAG"

      # 롤백 실행일 때는 되돌릴 SHA의 compose 파일을 써야 한다. HEAD를 체크아웃하면
      # 그 사이 compose가 바뀐 경우 옛 이미지에 새 compose를 섞어 배포하게 된다.
      - uses: actions/checkout@v7
        with:
          ref: ${{ steps.tag.outputs.value }}

      - name: Get runner public IP
        id: ip
        run: |
          IP=$(curl -fsS --retry 3 --retry-all-errors --connect-timeout 5 --max-time 15 https://api.ipify.org)
          [[ "$IP" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || { echo "::error::공인 IP 조회 실패: '$IP'"; exit 1; }
          echo "ipv4=$IP" >> "$GITHUB_OUTPUT"

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v6
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ap-northeast-2

      - name: Open SSH port for this runner
        id: open
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
          scp -i deploy_key.pem -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o ServerAliveInterval=15 -o ServerAliveCountMax=8 -o BatchMode=yes \
            deploy/docker-compose.yml \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }}:/opt/icuh/docker-compose.yml

      # 토큰을 stdin으로 넘긴다. 명령줄에 실리지 않으므로 EC2의 ps/히스토리에 남지 않는다.
      - name: Log in to GHCR on EC2
        id: ghcr_login
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | ssh -i deploy_key.pem -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o ServerAliveInterval=15 -o ServerAliveCountMax=8 -o BatchMode=yes \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} \
            'docker login ghcr.io -u ${{ github.actor }} --password-stdin'

      - name: Pull and start
        id: deploy
        continue-on-error: true
        env:
          IMAGE_TAG: ${{ steps.tag.outputs.value }}
        run: |
          ssh -i deploy_key.pem -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o ServerAliveInterval=15 -o ServerAliveCountMax=8 -o BatchMode=yes \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} \
            "cd /opt/icuh && IMAGE_TAG=${IMAGE_TAG} docker compose pull && IMAGE_TAG=${IMAGE_TAG} docker compose up -d --remove-orphans"

      - name: Health check
        id: health
        continue-on-error: true
        run: |
          ssh -i deploy_key.pem -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o ServerAliveInterval=15 -o ServerAliveCountMax=8 -o BatchMode=yes \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} bash -s <<'REMOTE'
          set -u
          deadline=$(( $(date +%s) + 240 ))
          ok=0
          while [ "$(date +%s)" -lt "$deadline" ]; do
            if curl -fsS -m 3 localhost:8081/health >/dev/null \
               && curl -fsS -m 3 localhost:8082/health >/dev/null \
               && curl -fsS -m 3 localhost:8083/health >/dev/null; then
              ok=1
              break
            fi
            sleep 5
          done
          if [ "$ok" -eq 1 ]; then
            echo "3개 앱 모두 정상"
            exit 0
          fi
          echo "헬스체크 실패 — 최근 로그"
          cd /opt/icuh && docker compose logs --tail 200
          exit 1
          REMOTE

      - name: Roll back failed deployment
        if: steps.deploy.outcome == 'failure' || steps.health.outcome == 'failure'
        run: |
          ROLLBACK_OK=1
          ssh -i deploy_key.pem -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o ServerAliveInterval=15 -o ServerAliveCountMax=8 -o BatchMode=yes \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} bash -s <<'REMOTE' || ROLLBACK_OK=0
          set -eu
          cd /opt/icuh
          if [ ! -s .last-good ]; then
            echo ".last-good이 없거나 비어 있어 자동 롤백을 못 한다. 수동 확인이 필요하다."
            exit 1
          fi
          PREV=$(cat .last-good)
          echo "직전 성공 태그로 되돌린다: $PREV"
          if ! IMAGE_TAG="$PREV" docker compose pull; then
            echo "롤백 대상 이미지(${PREV}) pull 실패 — 수동 개입 필요"
            exit 1
          fi
          IMAGE_TAG="$PREV" docker compose up -d --remove-orphans
          deadline=$(( $(date +%s) + 240 ))
          ok=0
          while [ "$(date +%s)" -lt "$deadline" ]; do
            if curl -fsS -m 3 localhost:8081/health >/dev/null \
               && curl -fsS -m 3 localhost:8082/health >/dev/null \
               && curl -fsS -m 3 localhost:8083/health >/dev/null; then
              ok=1
              break
            fi
            sleep 5
          done
          if [ "$ok" -eq 1 ]; then
            echo "롤백 후 정상 확인"
            exit 0
          fi
          echo "롤백했으나 헬스체크 여전히 실패 — 수동 개입 필요"
          docker compose logs --tail 200
          exit 1
          REMOTE
          if [ "$ROLLBACK_OK" -eq 1 ]; then
            echo "::error::배포 실패로 직전 성공 태그로 롤백했고, 롤백 후 헬스체크는 통과했다."
          else
            echo "::error::배포 실패 후 롤백도 정상화되지 않았다. 수동 개입이 필요하다."
          fi
          exit 1

      # 배포마다 모듈당 이미지가 하나씩 쌓이는데, 디스크 여유는 런북 Step 1에서 한 번
      # 볼 뿐이다. 2주(336h)보다 오래된 미사용 이미지를 정리해 디스크가 조용히 차는 것을
      # 막는다.
      #
      # label 필터가 안전장치의 핵심이다. 나이만 보고 지우면 런북 Step 4가 백업해 둔
      # icuh-platform:rollback — 구 앱을 8081로 되돌릴 유일한 수단 — 이 함께 지워진다.
      # 그 이미지는 Step 10의 docker rm 이후 아무 컨테이너도 참조하지 않고, 생성 시각은
      # 구 앱의 빌드 시점이라 336h를 한참 넘긴 상태이기 때문이다.
      # "직전 성공 이미지는 최근 것이라 안전하다"는 논리에 기대지 않는다 — 배포 간격이
      # 2주를 넘기면 그 전제가 깨진다. 이 파이프라인이 만든 이미지에만 Dockerfile의
      # LABEL re.kr.icuh.project=drought-platform이 붙어 있고, 정리 대상을 그것으로 한정한다.
      # 정리 실패가 배포 성공을 뒤집을 이유는 없으므로 || true.
      - name: Record last-good tag
        if: steps.health.outcome == 'success'
        env:
          IMAGE_TAG: ${{ steps.tag.outputs.value }}
        run: |
          ssh -i deploy_key.pem -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o ServerAliveInterval=15 -o ServerAliveCountMax=8 -o BatchMode=yes \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} \
            "echo ${IMAGE_TAG} > /opt/icuh/.last-good && (docker image prune -af --filter \"until=336h\" --filter \"label=re.kr.icuh.project=drought-platform\" || true)"

      # 이 스텝은 반드시 롤백 스텝 **뒤에** 온다. 롤백의 docker compose pull은 EC2의
      # docker 세션이 아직 GHCR에 로그인돼 있어야 성공하기 때문이다 — 이 로그아웃을
      # 롤백보다 앞으로 옮기면 롤백 pull이 인증 실패로 깨진다.
      # 성공 스텝(Record last-good tag)에 묶어 두면 실패한 배포에서는 GHCR 토큰이
      # 호스트의 ~/.docker/config.json에 그대로 남는다. 그래서 always()로 분리했다.
      # 조건이 ghcr_login 성공인 이유: 로그인 자체가 안 된 실행에서는 지울 토큰도 없고,
      # 그 시점엔 deploy_key.pem/호스트 접근이 아직 성립하지 않을 수 있다.
      - name: Log out of GHCR on EC2
        if: always() && steps.ghcr_login.outcome == 'success'
        run: |
          ssh -i deploy_key.pem -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o ServerAliveInterval=15 -o ServerAliveCountMax=8 -o BatchMode=yes \
            ${{ secrets.SSH_EC2_USER }}@${{ secrets.SSH_EC2_HOST }} \
            'docker logout ghcr.io' \
            || echo "::warning::EC2 GHCR 로그아웃에 실패했다 — 호스트의 ~/.docker/config.json을 직접 확인한다"

      # Open SSH port가 **실제로 실행됐을 때만** revoke한다. 두 가지를 동시에 만족해야 한다.
      #  (a) 그 앞(가드·태그 검증·IP 조회)에서 멈춰 규칙을 연 적도 AWS 자격증명도 없는
      #      실행에서는 돌면 안 된다 — revoke를 5회 시도하다 실패해 원래 실패 원인을
      #      가리는 두 번째 에러를 덧씌운다.
      #  (b) authorize가 실패한 실행에서는 **반드시** 돌아야 한다 — 앞선 실행의 revoke가
      #      실패해 남은 규칙 때문에 InvalidPermission.Duplicate로 실패하는 경우가 있고,
      #      그때 닫지 않으면 GitHub가 러너 간에 재사용하는 IP에 22번이 열린 채 남는다.
      # 부정(!= 'skipped')이 아니라 명시적 논리합으로 쓴 이유: 실행되지 않은 스텝의
      # outcome이 null인지 'skipped'인지는 러너에서 확인해 볼 방법이 없다. 이 형태는 두
      # 해석 모두에서 옳다 — null도 'skipped'도 양쪽 비교가 거짓이고, 'success'와
      # 'failure'만 참이 된다. 짧아 보인다고 부정형으로 되돌리지 않는다.
      - name: Close SSH port
        if: always() && (steps.open.outcome == 'success' || steps.open.outcome == 'failure')
        run: |
          for i in 1 2 3 4 5; do
            if aws ec2 revoke-security-group-ingress \
              --group-id ${{ secrets.AWS_SG_ID }} \
              --protocol tcp --port 22 \
              --cidr ${{ steps.ip.outputs.ipv4 }}/32; then
              echo "22번 포트 규칙 제거 완료"
              exit 0
            fi
            echo "revoke 실패 (시도 ${i}/5)"
            [ "$i" -lt 5 ] && sleep 5
          done
          echo "::error::22번 포트 규칙 제거에 5회 모두 실패했다 — 수동으로 보안그룹을 확인해야 한다"
          exit 1

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

PR을 만들어 머지한다. **머지 직후 자동 실행되는 첫 `Deploy`는 EC2 세팅(Task 5) 전이라
`Sync compose file`에서 실패하는 것이 정상이다** — Task 5의 "실행 순서" 참고. 그 실행에서도
`build-and-push`는 성공해 GHCR에 이미지가 올라가고, 그 이미지가 런북 Step 8이 받아 갈 대상이 된다.

- [ ] **Step 5: 첫 자동 배포를 확인한다**

Task 5(런북)를 끝낸 뒤 Actions → Deploy → Run workflow(`main`, `image_tag` 비움)로 실행한 run을 연다.
머지 직후의 실패한 run이 아니라 **이 수동 실행**이 파이프라인 전체를 처음 통과시키는 실행이다.
확인할 것:

1. `Guard against deploying a non-main ref`와 `Resolve image tag`가 통과했는지 (태그가 40자 SHA인지)
2. `Open SSH port` → `Close SSH port`가 짝으로 실행됐는지
3. `Health check`가 `3개 앱 모두 정상`을 출력했는지
4. `Record last-good tag`가 돌았고, 그 뒤 `Log out of GHCR on EC2`가 돌았는지

EC2에서:

```bash
cat /opt/icuh/.last-good        # 방금 커밋 SHA
docker compose -f /opt/icuh/docker-compose.yml ps
sudo grep -c ghcr.io ~/.docker/config.json || echo "로그아웃됨"
```

- [ ] **Step 6: 롤백 리허설을 한다**

실제로 필요할 때 처음 눌러보면 늦다. 지금 한 번 돌려본다.

1. `git log --oneline -3`으로 **직전** 커밋 SHA를 확인한다 (전체 40자: `git rev-parse HEAD~1`)
2. Actions → Deploy → Run workflow → `image_tag`에 그 SHA를 넣고 실행
3. `build-and-push`가 skip되고 `deploy`만 도는지 확인
4. EC2에서 `docker compose ps`로 이미지 태그가 바뀌었는지 확인
5. 다시 최신 SHA로 한 번 더 실행해 원복한다

Expected: 4번에서 이미지 태그가 지정한 SHA로 바뀌어 있어야 한다. 바뀌지 않았다면 compose가 `IMAGE_TAG`를 못 읽은 것이므로 `Pull and start` 스텝의 따옴표를 확인한다.

- [ ] **Step 7: 이전 프로젝트 워크플로가 꺼져 있는지 확인한다**

구 앱을 내렸으므로 `icuh-platform` 저장소의 `develop` 브랜치에 푸시가 들어가면 8081에 구 앱이 다시 뜬다.
실제 비활성화는 **런북 Step 9에서 구 컨테이너를 지우기 전에** 이미 해 둔다 — 컷오버 도중에 그 일이
벌어지지 않게 하려는 것이다. 여기서는 그 저장소의 Actions에서 해당 워크플로가 `Disabled`로 표시되는지
다시 확인만 한다.

---

## 완료 기준

- [ ] PR을 열면 `CI`가 돌고 테스트가 게이트로 동작한다
- [ ] `main` 머지 시 GHCR에 SHA 태그로 3개 이미지가 올라간다
- [ ] CI가 체크아웃한 트리만으로 빌드한 이미지가 설정 누락 없이 기동한다 (실패한다면 DB 도달 실패까지 간다)
- [ ] 세 컨테이너에 `mem_limit`이 걸려 있고 그 합이 인스턴스 메모리 안에 들어온다 (`docker stats`)
- [ ] `/opt/icuh/.last-good`의 태그로 `docker compose pull`이 실제로 성공한다
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
