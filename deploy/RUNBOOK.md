# EC2 최초 세팅 런북

EC2 한 대에 `public-api`(8081) · `admin-api`(8082) · `open-api`(8083) 세 컨테이너를 처음으로 올리는
수동 절차다. **사람이 SSH로 EC2에 접속해 순서대로 실행한다. 자동화 대상이 아니다.**

구성 자체(파일 위치, env 분리 이유, 로그 확인, admin-api 접근법, 롤백 개요)는 `deploy/README.md`에
있다. 이 문서는 그 구성을 처음 EC2에 앉히는 일회성 절차만 다룬다 — 겹치는 내용은 README를 가리키고
반복하지 않는다. 단, README의 롤백 절이 설명하는 Actions 기반 되돌리기(`deploy` 워크플로,
`image_tag` 입력)는 **아직 동작하지 않는다** — 그 job은 Task 6에서 추가되며, 지금
`.github/workflows/deploy.yml`에는 `build-and-push`만 있다. 그 전까지 되돌릴 일이 생기면 이 문서
자체의 수동 절차(바로 아래 "먼저 읽는다" 절의 명령, Step 4의 백업)를 쓴다.

## 시작 전에 준비할 것

- EC2 접속용 SSH 키, `<user>`, `<host>`
- `read:packages` 권한이 있는 GitHub PAT (ghcr.io 로그인용)
- 세 서비스의 실제 운영 값 — DB 접속 정보, CORS 허용 origin, S3 버킷/자격증명 등 (`deploy/.env.public.example` ·
  `deploy/.env.admin.example` · `deploy/.env.open.example` 참고, 항목 근거는 `RUNTIME_CONFIG.md`)
- 보안그룹을 수정할 수 있는 AWS 콘솔 접근권한

## 먼저 읽는다 — 파괴적 단계와 되돌리기

이 런북에서 절대 놓치면 안 되는 세 가지다.

1. **Step 8은 파괴적이다.** 8081에서 지금 서비스 중인 구 컨테이너를 멈추고 제거한다. **Step 4에서
   구 이미지를 `icuh-platform:rollback`으로 태그하고, 컨테이너의 실행 설정(환경변수·포트·볼륨)을
   `/opt/icuh/old-container-inspect.json` · `/opt/icuh/old-container-env.txt`로 남겨 두는 것이
   Step 8을 되돌릴 수 있게 만드는 유일한 장치다.** 이미지 태그만으로는 부족하다 — `docker rm`이
   구 컨테이너의 실행 설정까지 함께 지우기 때문이다. Step 4를 건너뛰거나 실패한 채로 Step 8을
   실행하지 않는다.
2. **되돌리는 명령 (Step 9에 나오는 것과 동일, 당황했을 때 바로 쓰라고 여기 다시 적는다):**

   ```bash
   docker compose down && docker run -d --name icuh_platform -p 8081:8081 icuh-platform:rollback
   ```

   이 명령은 최소 기동만 한다. 원래 포트/환경변수/볼륨 옵션은 Step 4에서 남긴
   `/opt/icuh/old-container-inspect.json` · `/opt/icuh/old-container-env.txt`를 보고 `docker run`
   옵션에 반영한다(`old-container-env.txt`에는 평문 시크릿이 들어 있다 — Step 4 설명 참고). 두 파일이
   없거나 유실됐다면 참고용으로 `public-api/.github/workflows/cicd.yml`의 `Deploy to EC2` 스텝에도
   동일한 실행 옵션이 남아 있다(이 저장소로 옮겨 오기 전 구 저장소의 워크플로 파일과 바이트 단위로
   동일함을 확인했다). 아래 "브리프와 저장소가 어긋나는 부분" 참고.
3. **컨테이너 이름 `icuh_platform`은 추측이다.** 이전 프로젝트의 워크플로가 쓰던 이름을 그대로 적어
   둔 것뿐이다. 이 이름은 **Step 4의 `OLD=icuh_platform` 한 줄에서만** 입력받는다 — **거기서 반드시
   Step 1에서 직접 확인한 `docker ps -a` 결과의 실제 이름으로 바꿔 적는다.** Step 8은 그 값(`$OLD`)을
   그대로 재사용할 뿐 다시 묻지 않는다. Step 8 전체가 가드에서 시작해 재시작 확인까지 하나의 `&&`
   체인이라, `OLD`가 설정돼 있지 않으면(예: 접속이 끊겼다가 다시 붙어 셸 세션이 바뀐 경우) 맨 앞
   가드가 실패해 그 뒤로 이어진 모든 명령이 실행되지 않는다 — **셸 자체가 멈추는 것은 아니다,** 체인이
   끊길 뿐이다. `중단됨` 메시지가 보이면 그 체인의 어떤 명령도 실행되지 않은 것이니, Step 4의 `OLD=`
   블록부터 다시 실행한다.
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
스왑을 먼저 만든다. `docker ps -a`에서 확인한 실제 컨테이너 이름은 Step 4의 `OLD=`에 그대로 써야
하니 잘 적어 둔다.

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

- [ ] **Step 4: 구 앱 이미지와 실행 설정을 백업한다 (되돌릴 수 있게)**

**이 스텝이 Step 8을 되돌릴 수 있게 만드는 유일한 장치다. 건너뛰지 않는다.**

백업 파일을 쓸 자리가 있어야 한다. Step 5가 `/opt/icuh/logs/public`까지 포함해 배포 디렉터리를
제대로 만들지만, 그 전에 최상위 `/opt/icuh`만 여기서 먼저 만들어 둔다 — 안 그러면 아래 리다이렉트가
"디렉터리 없음"으로 실패한다. (Step 5를 이 스텝보다 앞으로 당기는 대신, mkdir을 이렇게 나눠 순서
번호를 브리프와 그대로 맞췄다. Step 5에서 다시 mkdir/chown을 실행해도 안전하다 — 이미 있는 디렉터리에
대한 재실행이라 아무 것도 깨지지 않는다.)

```bash
sudo mkdir -p /opt/icuh
sudo chown -R "$USER":"$USER" /opt/icuh
```

컨테이너 이름은 아래에서 **한 번만** 적는다. 나머지 명령은 모두 이 값(`$OLD`)을 그대로 쓴다 —
같은 이름을 두 군데 이상에 따로 적으면 한쪽만 고치고 다른 쪽을 놓치는 사고가 나기 때문에, 애초에
고칠 곳을 하나로 줄인다. `OLD`는 지금 이 셸 세션에만 남는 변수다 — 중간에 접속이 끊겼다가 다시
붙은 뒤에 이어서 진행한다면, Step 8을 실행하기 전에 이 블록부터 다시 실행해 `OLD`를 다시 설정한다.

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

이미지 태그만으로는 부족하다. Step 8의 `docker rm`은 컨테이너의 실행 설정 — 환경변수 십여 개, 포트
매핑, 볼륨 마운트 — 까지 함께 지운다. 그래서 `docker inspect` 전체 출력과 환경변수 목록도 파일로
남긴다.

**`old-container-env.txt`에는 구 앱의 DB 비밀번호와 AWS 시크릿 키가 평문으로 들어 있다.** 방금
`chmod 600`으로 다른 계정이 읽지 못하게 좁혔지만 그걸로 끝이 아니다 — 새 배포가 충분히 안정적으로
자리 잡아 롤백할 일이 더는 없다고 판단되면, `old-container-inspect.json`과 `old-container-env.txt`
두 파일을 반드시 삭제한다.

**줄 수만 세는 확인은 속는다.** `$OLD`에 존재하지 않는 컨테이너 이름을 넣으면 `docker inspect`는
표준출력에 `[]` 딱 한 줄만 내보내고 종료 코드는 0이 아니게 끝나지만, 그 상태에서도 리다이렉트는
그 한 줄을 파일에 그대로 써 버린다 — "파일이 0줄보다 많다"는 확인은 이 경우에도 통과해, 내용이 없는
백업을 성공으로 착각하게 만든다. 그래서 줄 수 대신 파일 **내용**을 본다:

```bash
grep -q '"Id"' /opt/icuh/old-container-inspect.json \
  && echo "설정 백업 OK" \
  || echo "실패: '$OLD' 컨테이너를 찾지 못했다. Step 1 출력에서 실제 이름을 확인하고 다시 실행한다."
test -s /opt/icuh/old-container-env.txt \
  && echo "환경변수 백업 OK ($(wc -l < /opt/icuh/old-container-env.txt)줄)" \
  || echo "실패: 환경변수를 받지 못했다."
docker images | grep icuh-platform
```

`설정 백업 OK`·`환경변수 백업 OK`가 둘 다 나오고, 마지막 줄에 `icuh-platform:rollback`이 보이면
성공이다. 이 이미지와 두 파일이 있으면 언제든 구 앱을 8081로 되돌릴 수 있다 (맨 위 "먼저 읽는다"
절의 되돌리기 명령 참고).

실행 결과:

```
(OLD_IMAGE 값)


(설정 백업 / 환경변수 백업 확인 두 줄의 출력)


(docker images | grep icuh-platform 출력 — icuh-platform:rollback 줄이 보여야 한다)

```

- [ ] **Step 5: 배포 디렉터리를 만든다**

```bash
sudo mkdir -p /opt/icuh/logs/public
sudo chown -R "$USER":"$USER" /opt/icuh
```

`/opt/icuh` 자체는 Step 4에서 백업 파일을 쓰려고 이미 만들어 뒀다 — 여기서 다시 만들어도 안전하다
(이미 있으면 `mkdir -p`는 아무 것도 하지 않고, `chown -R`도 이미 소유한 파일에 다시 걸어도 무해하다).
이 스텝이 실제로 새로 만드는 것은 `logs/public` 하위 디렉터리다.

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

**여기가 파괴적 단계다.** 컨테이너 이름은 Step 4에서 설정한 `$OLD`를 그대로 쓴다 — 여기서 새로
적지 않는다(이름을 두 곳에 따로 적으면 한쪽만 고치는 사고가 날 수 있어서다. `icuh_platform`이 이전
워크플로가 쓰던 이름을 그대로 옮겨 적은 추측값이라는 점과, 그 값을 어디서 확정하는지는 Step 4 참고).

아래는 한 덩어리로 붙여넣는 명령 **하나**다. 가드부터 재시작 확인까지 전부 `&&`로 묶여 있어, 중간
어느 지점이 실패하든 그 뒤는 전혀 실행되지 않고 곧바로 `중단됨` 메시지가 출력된다 — 블록을 둘로
나눠서 사이에 확인만 끼워 넣으면 그 경계에서 제어 흐름이 새어 나가기 때문에, 하나의 체인으로만
안전하다. `OLD`가 이 셸 세션에 남아 있지 않으면(재접속 등으로 사라졌으면) 맨 앞의 가드가 실패한다.
**가드가 셸 자체를 멈추는 것은 아니다** — `&&`는 대화형이든 비대화형이든 동일하게 동작해서, 가드가
실패한 순간 그 뒤에 연결된 `docker stop`·`docker rm`·재시작 확인·`docker compose up`·
`docker compose ps`가 전부 건너뛰어질 뿐이다. 체인 중간의 `! docker ps -a --filter ... | grep -q .`는
구 컨테이너가 실제로 사라졌는지 재확인하는 지점이다 — 아직 남아 있으면 여기서 실패해 뒤의
`docker compose up -d`로 넘어가지 않는다.

```bash
: "${OLD:?Step 4의 OLD가 설정돼 있지 않다. 재접속했다면 Step 4를 다시 실행한다.}" \
  && docker stop "$OLD" \
  && docker rm "$OLD" \
  && ! docker ps -a --filter "name=^/${OLD}$" --format '{{.Names}}' | grep -q . \
  && cd /opt/icuh \
  && IMAGE_TAG=latest docker compose up -d \
  && docker compose ps \
  || echo "중단됨 — 위 출력을 확인한다. OLD 미설정, 구 컨테이너 제거 실패, compose 기동 실패 중 하나다."
```

`중단됨`이 보이면, 그 직전까지 화면에 실제로 찍힌 것이 이 체인이 실행한 마지막 지점이다 — 그 뒤로는
아무 것도 실행되지 않았다. `docker compose ps`에 `public-api` · `admin-api` · `open-api` 세
서비스가 `Up`으로 보이고 `중단됨`이 나오지 않았으면 성공이다. 문제가 생기면 바로 위 "먼저 읽는다"
절의 되돌리기 명령을 쓴다 — Step 4의 백업이 없으면 되돌릴 수 없다.

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
— 이 명령 자체는 최소 기동만 한다. 원래 포트/환경변수/볼륨 옵션은 Step 4에서 남긴
`/opt/icuh/old-container-inspect.json` · `/opt/icuh/old-container-env.txt`를 보고 반영한다. 두 파일이
없다면 참고용으로 `public-api/.github/workflows/cicd.yml`의 Deploy 스텝을 대신 본다.

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
- Step 4는 원래 이미지 태그(`icuh-platform:rollback`)만 백업했다. 이미지만으로는 부족하다 —
  Step 8의 `docker rm`이 구 컨테이너의 실행 설정(환경변수 십여 개, 포트 매핑, 볼륨 마운트)까지 함께
  지우기 때문이다. 그래서 Step 4에 `docker inspect` 전체 출력과 환경변수 목록을 파일로 남기는 절차를
  추가했다(`/opt/icuh/old-container-inspect.json` · `/opt/icuh/old-container-env.txt`). 뒤의
  파일에는 구 앱의 DB 비밀번호·AWS 시크릿 키가 평문으로 들어가므로 `chmod 600`으로 권한을 좁히고,
  롤백 가능성이 없어지면 삭제하도록 문서에 명시했다. 이 캡처가 `/opt/icuh` 존재를 전제하므로, Step 5의
  디렉터리 생성 중 최상위 `/opt/icuh` 부분만 Step 4로 앞당겼다(스텝 번호는 브리프와 동일하게 유지).
- 브리프 원문의 Step 8은 `docker stop icuh_platform && docker rm icuh_platform`처럼 추측 컨테이너
  이름을 그 자리에 직접 적었다. Step 8을 안전하게 고치는 데 세 번을 거쳤다. ① 이름을 두 곳(백업
  캡처와 이 명령)에 따로 적으면 운영자가 한쪽만 고치고 다른 쪽을 놓칠 수 있어, Step 4에서 설정한
  `$OLD`를 재사용하도록 바꿨다. ② `: "${OLD:?...}"` 가드를 독립된 줄로 두었더니, 그 형태는 비대화형
  셸에서만 전체를 중단시키고, 대화형 SSH 세션에 블록을 붙여넣는 실제 상황에서는 가드가 실패 메시지만
  내고 다음 줄로 그대로 넘어갔다 — 그래서 가드를 `&&`로 `docker stop`·`docker rm`에 체인으로 묶었다.
  ③ 그런데 그 체인을 재시작 확인과 별도 블록으로 나눴더니, 그 경계에서 제어 흐름이 새어 나갔다 —
  `docker compose up -d`가 확인 결과와 무관하게 무조건 실행됐고, 심지어 `OLD`가 비어 있으면
  `--filter "name=^/${OLD}$"`가 아무 것도 매치하지 않아 `grep -q .`가 실패해 `||` 분기가 "제거 확인 —
  신규 컨테이너를 올린다"라는 거짓 안전 신호까지 냈다. 최종적으로 가드부터 `docker compose ps`까지
  전부 하나의 `&&` 체인으로 묶고, 맨 끝에 `|| echo "중단됨..."`을 붙여 어느 지점에서 실패하든 그 뒤
  전부가 건너뛰어지고 실패가 눈에 보이도록 했다.
