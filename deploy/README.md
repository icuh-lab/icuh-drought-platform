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

`public-api`만 `./logs/public`에 파일 로그를 남긴다(`application-prod.yml`이 `logback-prod.xml`을 통해
`${user.home}/log/spring`에 쓰도록 설정되어 있다). `admin-api`·`open-api`는 별도 logback 설정이 없어 표준출력으로만
로그를 낸다 — `docker logs icuh-admin-api` / `docker logs icuh-open-api`로 확인한다.

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
