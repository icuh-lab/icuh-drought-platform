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
