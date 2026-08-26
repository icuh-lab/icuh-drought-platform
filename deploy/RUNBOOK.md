# EC2 최초 세팅 런북

EC2 한 대에 `public-api`(8081) · `admin-api`(8082) · `open-api`(8083) 세 컨테이너를 처음으로 올리는
수동 절차다. **사람이 SSH로 EC2에 접속해 순서대로 실행한다. 자동화 대상이 아니다.**

구성 자체(파일 위치, env 분리 이유, 로그 확인, admin-api 접근법, 롤백 개요)는 `deploy/README.md`에
있다. 이 문서는 그 구성을 처음 EC2에 앉히는 일회성 절차만 다룬다 — 겹치는 내용은 README를 가리키고
반복하지 않는다.

## 시작 전에 준비할 것

- EC2 접속용 SSH 키, `<user>`, `<host>`
- `read:packages` 권한이 있는 GitHub PAT (ghcr.io 로그인용)
- 세 서비스의 실제 운영 값 — DB 접속 정보, CORS 허용 origin, S3 버킷/자격증명 등 (`deploy/.env.public.example` ·
  `deploy/.env.admin.example` · `deploy/.env.open.example` 참고, 항목 근거는 `RUNTIME_CONFIG.md`)
- 보안그룹을 수정할 수 있는 AWS 콘솔 접근권한

## 먼저 읽는다 — 파괴적 단계와 되돌리기

이 런북에서 절대 놓치면 안 되는 세 가지다.

1. **Step 8은 파괴적이다.** 8081에서 지금 서비스 중인 구 컨테이너를 멈추고 제거한다. **Step 4에서
   구 이미지를 `icuh-platform:rollback`으로 태그해 두는 것이 Step 8을 되돌릴 수 있게 만드는 유일한
   장치다.** Step 4를 건너뛰거나 실패한 채로 Step 8을 실행하지 않는다.
2. **되돌리는 명령 (Step 9에 나오는 것과 동일, 당황했을 때 바로 쓰라고 여기 다시 적는다):**

   ```bash
   docker compose down && docker run -d --name icuh_platform -p 8081:8081 icuh-platform:rollback
   ```

   원래 실행 옵션(환경변수 등)은 참고용으로 `public-api/.github/workflows/cicd.yml`의 `Deploy to EC2`
   스텝에 남아 있다. (브리프 원문은 이 파일을 `icuh-platform/.github/workflows/cicd.yml`로 가리키는데,
   현재 저장소에는 그 경로가 없다 — 멀티모듈 통합 전 구 저장소의 워크플로가 `public-api/` 아래로
   들어와 있다. 아래 "브리프와 저장소가 어긋나는 부분" 참고.)
3. **Step 8의 컨테이너 이름 `icuh_platform`은 추측이다.** 이전 프로젝트의 워크플로가 쓰던 이름을
   그대로 적어 둔 것뿐이다. **Step 8을 실행하기 전, 반드시 Step 1에서 직접 확인한 `docker ps -a`
   결과의 실제 이름을 쓴다.** 이름이 다르면 아래 명령의 `icuh_platform`을 그 값으로 바꿔서 실행한다.
4. **Step 11에서 8082는 보안그룹에 절대 추가하지 않는다.** `admin-api`는 승인·반려·병합 엔드포인트를
   포함해 인증이 전혀 없다. (`deploy/docker-compose.yml`도 `admin-api`를 `127.0.0.1:8082:8082`로
   호스트 루프백에만 바인딩해 두어서, 설령 보안그룹에 실수로 규칙이 생기더라도 컨테이너 자체가 외부
   연결을 받지 않는다 — 그래도 규칙은 추가하지 않는다.)

---

- [ ] **Step 1: 현재 상태를 기록한다**

```bash
ssh -i <키> <user>@<host>
docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
docker compose version || echo "COMPOSE-V2-NONE"
df -h /
free -m
swapon --show || echo "SWAP-NONE"
```

특히 **메모리**를 확인한다. 총 1GB 인스턴스라면 JVM 3개가 동시에 뜨지 못한다 — 그 경우 Step 2에서
스왑을 먼저 만든다. `docker ps -a`에서 확인한 실제 컨테이너 이름은 Step 8에서 그대로 써야 하니
잘 적어 둔다.

실행 결과:

```
(docker ps -a 출력)


(docker compose version 출력)


(df -h / 출력)


(free -m 출력)


(swapon --show 출력)

```

- [ ] **Step 2: (메모리가 2GB 미만일 때만) 스왑을 만든다**

Step 1에서 확인한 메모리가 2GB 미만이 아니면 이 스텝은 건너뛴다.

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -m
```

실행 결과:

```
(건너뛰었으면 "N/A — 메모리 충분"이라고만 적는다. 실행했으면 마지막 free -m 출력을 붙인다)

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

실행 결과:

```
(건너뛰었으면 "N/A — 이미 compose v2 있음"이라고만 적는다. 실행했으면 docker compose version 출력을 붙인다)

```

- [ ] **Step 4: 구 앱 이미지를 백업한다 (되돌릴 수 있게)**

**이 스텝이 Step 8을 되돌릴 수 있게 만드는 유일한 장치다. 건너뛰지 않는다.**

```bash
OLD_IMAGE=$(docker inspect --format '{{.Config.Image}}' icuh_platform)
echo "$OLD_IMAGE"
docker tag "$OLD_IMAGE" icuh-platform:rollback
docker images | grep icuh-platform
```

`OLD_IMAGE`를 구할 때도 `icuh_platform`은 Step 1에서 확인한 실제 컨테이너 이름으로 바꿔서 실행한다.
`icuh-platform:rollback` 태그가 `docker images` 출력에 보이면 성공이다. 이 이미지가 있으면 언제든
구 앱을 8081로 되돌릴 수 있다 (맨 위 "먼저 읽는다" 절의 되돌리기 명령 참고).

실행 결과:

```
(OLD_IMAGE 값)


(docker images | grep icuh-platform 출력 — icuh-platform:rollback 줄이 보여야 한다)

```

- [ ] **Step 5: 배포 디렉터리를 만든다**

```bash
sudo mkdir -p /opt/icuh/logs/public
sudo chown -R "$USER":"$USER" /opt/icuh
```

`logs/public`만 만드는 이유: `public-api`만 파일 로그를 남기고(`docker-compose.yml`이 이 디렉터리를
`/root/log/spring`에 마운트한다), `admin-api`·`open-api`는 표준출력으로만 로그를 낸다 — 필요하면
`docker logs icuh-admin-api` / `docker logs icuh-open-api`로 본다. 자세한 내용은 `deploy/README.md`의
"로그" 절 참고.

- [ ] **Step 6: 서비스별 `.env.<name>`을 작성한다**

저장소의 `deploy/.env.public.example` · `deploy/.env.admin.example` · `deploy/.env.open.example`을
기준으로 실제 값을 채운다. 파일이 **세 개로 나뉜 이유**: 한 `.env`를 세 컨테이너가 공유하면 admin-api
전용 시크릿이 인터넷에 노출된 public-api·open-api 프로세스 환경에도 실리기 때문이다 (자세한 설명은
`deploy/README.md`). 최종적으로 EC2에는 아래 세 파일이 남는다.

| 파일 | 읽는 서비스 |
|---|---|
| `/opt/icuh/.env.public` | `public-api` (8081) |
| `/opt/icuh/.env.admin` | `admin-api` (8082, 보안그룹 미개방) |
| `/opt/icuh/.env.open` | `open-api` (8083) |

```bash
vi /opt/icuh/.env.public   # CHANGEME를 모두 실제 값으로
vi /opt/icuh/.env.admin
vi /opt/icuh/.env.open
chmod 600 /opt/icuh/.env.public /opt/icuh/.env.admin /opt/icuh/.env.open
grep -c CHANGEME /opt/icuh/.env.public /opt/icuh/.env.admin /opt/icuh/.env.open   # 모두 0 이어야 한다
```

세 파일 다 `CHANGEME`가 0개로 나와야 한다. 하나라도 남아 있으면 그 서비스는 잘못된 값으로 뜬다.

실행 결과:

```
(grep -c CHANGEME 세 줄 출력 — 예: /opt/icuh/.env.public:0 형태로 세 줄)

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

Expected: 3개 이미지(`public-api` · `admin-api` · `open-api`)가 받아진다. 실패하면 PAT 권한
(`read:packages`)이나 GHCR 패키지의 visibility/Actions access 설정을 의심한다.

실행 결과:

```
(docker compose pull 출력)

```

- [ ] **Step 8: 구 컨테이너를 내리고 신 앱을 올린다**

**여기가 파괴적 단계다.** 컨테이너 이름은 반드시 **Step 1에서 확인한 실제 값**을 쓴다. 아래 명령의
`icuh_platform`은 이전 워크플로가 쓰던 이름을 그대로 옮겨 적은 것뿐이며, 실제로 다를 수 있다.

```bash
docker stop icuh_platform && docker rm icuh_platform
cd /opt/icuh && IMAGE_TAG=latest docker compose up -d
docker compose ps
```

`docker compose ps`에 `public-api` · `admin-api` · `open-api` 세 서비스가 `Up`으로 보이면 성공이다.
문제가 생기면 바로 위 "먼저 읽는다" 절의 되돌리기 명령을 쓴다 — Step 4의 백업이 없으면 되돌릴 수 없다.

실행 결과:

```
(docker compose ps 출력)

```

- [ ] **Step 9: 헬스체크로 확인한다**

```bash
curl -fsS localhost:8081/health && echo " public OK"
curl -fsS localhost:8082/health && echo " admin OK"
curl -fsS localhost:8083/health && echo " open OK"
```

셋 다 성공(종료 코드 0)해야 한다. 세 앱 모두 `GET /health`가 있고 상태코드는 200이지만, 응답 본문은
다르다 — `public-api`·`admin-api`는 본문에 문자열 `OK`가 실려 `curl`이 그대로 찍은 뒤 뒤에 붙인
문구가 이어진다(예: `OK public OK`). `open-api`는 본문이 비어 있어 뒤에 붙인 문구만 보인다(예:
` open OK`). 본문 유무와 무관하게 종료 코드 0(=200 응답)이면 정상이다.

실패하면 `docker compose logs <service>`로 원인을 본다. 대개 해당 서비스의 `.env.<name>`의 DB 접속
정보 문제다.

**되돌리려면:** `docker compose down && docker run -d --name icuh_platform -p 8081:8081 icuh-platform:rollback`
(원래 실행 옵션은 `public-api/.github/workflows/cicd.yml`의 Deploy 스텝 참고)

실행 결과:

```
(세 curl 명령의 출력과 종료 여부)

```

- [ ] **Step 10: 첫 성공 태그를 기록한다**

```bash
git rev-parse HEAD   # 로컬에서 현재 main의 SHA
```

EC2에서:

```bash
echo <그 SHA> > /opt/icuh/.last-good
```

실행 결과:

```
(기록한 커밋 SHA)

```

- [ ] **Step 11: 보안그룹을 정리한다**

AWS 콘솔 → EC2 → 보안그룹 → 인바운드 규칙:

- 8081 — 이미 열려 있다 (구 앱이 쓰던 것). 유지.
- **8083 — 새로 추가한다.** 소스는 프론트가 접근할 범위로.
- **8082 — 추가하지 않는다.** `admin-api`는 인증이 없다. (승인·반려·병합 엔드포인트까지 전부
  무방비로 열려 있다는 뜻이다.) 접근이 필요하면 보안그룹을 열지 말고 SSH 터널을 쓴다 —
  방법은 `deploy/README.md`의 "admin-api 접근" 절 참고.
- 22 — Actions가 배포 때 임시로 열고 닫으므로 상시 규칙은 두지 않는다(관리자 접속용 고정 IP 규칙은
  별개로 유지해도 된다).

실행 결과:

```
(정리 후 인바운드 규칙 스냅샷 — 8081/8083 존재, 8082 부재, 22 상시 규칙 부재를 확인했다고 기록)

```

- [ ] **Step 12: 런북 결과를 커밋한다**

위 각 스텝의 "실행 결과" 슬롯을 모두 채운 뒤:

```bash
git add deploy/RUNBOOK.md
git commit -m "docs: EC2 최초 세팅 런북과 실행 결과 기록"
```

> 이 문서를 저장소에 처음 추가하는 커밋(본 작업)은 위 절차를 아직 아무것도 실행하지 않은 상태로
> 만들어졌으므로, "실행 결과" 슬롯이 비어 있다. 실제 EC2 세팅을 수행한 뒤 이 커밋 메시지로 결과를
> 채워 다시 커밋하는 것은 운영자의 몫이다.

---

## 브리프와 저장소가 어긋나는 부분

- Step 9의 되돌리기 안내가 가리키는 워크플로 파일 경로는 원문에서 `icuh-platform/.github/workflows/cicd.yml`이지만,
  현재 저장소에는 이 경로가 없다. 동일한 내용(구 컨테이너 실행 옵션)은 `public-api/.github/workflows/cicd.yml`의
  `Deploy to EC2` 스텝에 있다 — 위 두 곳 모두 이 경로로 고쳐 적었다.
- Step 9의 헬스체크 응답 본문은 태스크 안내에서 "public-api는 빈 본문, 나머지 둘은 `OK`"라고 주어졌으나,
  세 `HealthController` 소스를 직접 확인한 결과는 반대다 — `public-api`·`admin-api`가 문자열 `OK`를 반환하고
  (`public-api/src/main/java/.../health/api/HealthController.java`, `admin-api/src/main/java/.../HealthController.java`),
  `open-api`가 `ResponseEntity.build()`로 빈 본문을 반환한다
  (`open-api/src/main/java/.../HealthController.java`). 이 문서는 소스 코드 기준(반대)으로 작성했다.
