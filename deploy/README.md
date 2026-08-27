# 배포 구성

EC2 한 대에 public-api(8081) · admin-api(8082) · open-api(8083) 세 컨테이너를 올린다.

## 지금 EC2에 실제로 떠 있는 것 (2026-08-27)

**이 세 컨테이너가 전부가 아니다. 구 API가 8080에서 함께 돌고 있다.** 신규 배포는 구 앱을 교체한
것이 아니라 **병행**으로 올라갔다 — 경위는 `RUNBOOK.md`의 "실행 완료 기록" 절에 있다.

| 포트 | 컨테이너 | 관리 주체 | 외부 노출 |
|---|---|---|---|
| 8080 | `icuh_platform_api` | 구 저장소 `icuh-lab/icuh-platform-api`의 워크플로 (여전히 active) | 열림 |
| 8081 | `icuh-public-api` | 이 저장소의 `deploy.yml` | 열림 |
| 8082 | `icuh-admin-api` | 이 저장소의 `deploy.yml` | 차단 (보안그룹 미개방 + 루프백 바인딩) |
| 8083 | `icuh-open-api` | 이 저장소의 `deploy.yml` | **미개방** — 프론트(Caddy) 배포 시 Caddy IP에만 열 예정 |

주의할 점 셋:

- **`docker compose down`은 8080을 건드리지 않는다.** 구 API는 compose 밖에서 도는 별개
  컨테이너다. 반대로 구 저장소 워크플로가 배포돼도 8081~8083에는 영향이 없다
  (`-p 8080:8080`으로 자기 컨테이너만 교체한다).
- **구 컨테이너 5개가 `exited` 상태로 남아 있고, 그것이 현재의 롤백 수단이다.** `docker rm`이나
  `docker system prune -a`를 실행하지 않는다.
- **`icuh-lab/icuh-platform-fo`의 워크플로가 켜져 있고 80번 포트에 배포한다.** 프론트를 Caddy로
  올릴 때 충돌하므로 그 전에 정리한다.

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
