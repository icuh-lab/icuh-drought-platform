# EC2 최초 세팅 런북

EC2 한 대에 `public-api`(8081) · `admin-api`(8082) · `open-api`(8083) 세 컨테이너를 처음으로 올리는
수동 절차다. **사람이 SSH로 EC2에 접속해 순서대로 실행한다. 자동화 대상이 아니다.**

구성 자체(파일 위치, env 분리 이유, 로그 확인, admin-api 접근법, 롤백 개요)는 `deploy/README.md`에
있다. 이 문서는 그 구성을 처음 EC2에 앉히는 일회성 절차만 다룬다 — 겹치는 내용은 README를 가리키고
반복하지 않는다.

파이프라인은 지금 이렇게 되어 있다. `.github/workflows/deploy.yml`에 job이 둘 있다 —
`build-and-push`(3개 모듈 이미지를 빌드해 GHCR에 푸시)와 `deploy`(EC2에 compose 파일을 올리고
`docker compose pull/up` → 헬스체크 → 실패하면 직전 성공 태그로 자동 롤백). `main`으로의 push와
Actions의 수동 실행(`workflow_dispatch`)에서 돈다. 따라서 README의 롤백 절이 설명하는 Actions 기반
되돌리기는 **동작한다.**

다만 자동 롤백은 EC2의 `/opt/icuh/.last-good`이 가리키는 이미지가 있어야 성립하고, 그 파일은 이
런북 **Step 12에서 처음 만들어진다.** 즉 **이 런북을 끝내기 전까지는 자동 롤백이 없다** — 그 전에
되돌릴 일이 생기면 이 문서 자체의 수동 절차(바로 아래 "먼저 읽는다" 절의 명령, Step 4의 백업)를 쓴다.

## 실행 순서 — 이 런북은 언제 실행하나

이 런북만 따로 실행하는 것이 아니다. 아래 순서의 3번이다.

1. **`feat/cicd-pipeline`을 `main`에 머지한다.**

2. **그 push로 `Deploy` 워크플로가 자동 실행된다. 이 첫 실행은 실패하는 것이 정상이다.**
   미리 알고 있지 않으면 파이프라인이 고장 난 것으로 오해하기 쉬우므로 여기 적어 둔다.
   - `build-and-push` job은 **성공한다.** GHCR에 3개 이미지가 올라간다 — 아래 Step 8이 받아 갈
     이미지가 이때 생긴다. 이 런북을 시작하려면 이 job이 성공해 있어야 한다.
   - `deploy` job은 **`Sync compose file` 스텝에서 실패한다.** EC2에 `/opt/icuh` 디렉터리가 아직
     없어 `scp`가 실패하기 때문이다. 이 실패는 안전하다 — 컨테이너를 아직 아무것도 건드리지 않았고,
     `Pull and start`·`Health check`가 실행되지 않았으므로 롤백 스텝은 조건이 거짓이라 건너뛰며
     (`.last-good`도 아직 없으니 롤백할 대상 자체가 없다), `Close SSH port`가 임시로 열었던 22번
     규칙을 되돌린다.
   - 실패한 run의 로그에서 `Sync compose file`이 실패 지점이 맞는지 한 번 확인하고 넘어간다.
     그 앞에서 실패했다면 이 런북이 아니라 GitHub Secrets(Step 14)부터 의심한다.

3. **이 런북을 Step 1부터 Step 15까지 실행한다.**

4. **런북이 끝나면 Actions → Deploy → Run workflow를 `main`에서, `image_tag`는 비운 채 다시 돌린다.**
   이번에는 끝까지 통과해야 한다. 파이프라인 전체(빌드 → 푸시 → SSH 배포 → 헬스체크 → `.last-good`
   갱신)가 실제로 도는지 확인하는 첫 실행이다. 여기서 실패하면 그때는 진짜 문제다.

## 시작 전에 준비할 것

- EC2 접속용 SSH 키, `<user>`, `<host>`
- `read:packages` 권한이 있는 GitHub PAT (ghcr.io 로그인용)
- 세 서비스의 실제 운영 값 — DB 접속 정보, CORS 허용 origin, S3 버킷/자격증명 등 (`deploy/.env.public.example` ·
  `deploy/.env.admin.example` · `deploy/.env.open.example` 참고, 항목 근거는 `RUNTIME_CONFIG.md`)
- 보안그룹을 수정할 수 있는 AWS 콘솔 접근권한
- 이 저장소와 구 저장소(`icuh-platform`) 양쪽의 GitHub 설정 권한 (Step 9, Step 14에서 쓴다)

## 먼저 읽는다 — 파괴적 단계와 되돌리기

이 런북에서 절대 놓치면 안 되는 네 가지다.

1. **Step 10은 파괴적이다.** 8081에서 지금 서비스 중인 구 컨테이너를 멈추고 제거한다. **Step 4에서
   구 이미지를 `icuh-platform:rollback`으로 태그하고, 컨테이너의 실행 설정(환경변수·포트·볼륨)을
   `/opt/icuh/old-container-inspect.json` · `/opt/icuh/old-container-env.txt`로 남겨 두는 것이
   Step 10을 되돌릴 수 있게 만드는 유일한 장치다.** 이미지 태그만으로는 부족하다 — `docker rm`이
   구 컨테이너의 실행 설정까지 함께 지우기 때문이다. Step 4를 건너뛰거나 실패한 채로 Step 10을
   실행하지 않는다.
2. **되돌리는 명령 (Step 11에 나오는 것과 동일, 당황했을 때 바로 쓰라고 여기 다시 적는다):**

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
   Step 1에서 직접 확인한 `docker ps -a` 결과의 실제 이름으로 바꿔 적는다.** Step 10은 그 값(`$OLD`)을
   그대로 재사용할 뿐 다시 묻지 않는다. Step 10 전체가 가드에서 시작해 재시작 확인까지 하나의 `&&`
   체인이라, `OLD`(또는 Step 7의 `TAG`)가 설정돼 있지 않으면(예: 접속이 끊겼다가 다시 붙어 셸 세션이
   바뀐 경우) 맨 앞 가드가 실패해 그 뒤로 이어진 모든 명령이 실행되지 않는다 — **셸 자체가 멈추는
   것은 아니다,** 체인이 끊길 뿐이다. `중단됨` 메시지가 보이면 그 체인의 어떤 명령도 실행되지 않은
   것이니, Step 7의 `TAG=`와 Step 4의 `OLD=` 블록부터 다시 실행한다.
4. **Step 13에서 8082는 보안그룹에 절대 추가하지 않는다.** `admin-api`는 승인·반려·병합 엔드포인트를
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

`free -m`의 총 메모리는 한 군데 더 쓰인다. `deploy/docker-compose.yml`은 서비스마다 `mem_limit`을
걸어 두었는데(public 768m / admin 512m / open 512m, 합계 1792m), 이 값은 인스턴스 크기를 모른 채
정한 보수적인 기본값이다. **여기서 잰 총 메모리에서 OS·도커 몫(넉넉히 512m)을 뺀 값이 1792m보다
작으면 로컬 체크아웃에서 `deploy/docker-compose.yml`의 `mem_limit`을 그에 맞게 줄인다.** 한도를
아예 지우면 안 된다 — Dockerfile의 `-XX:MaxRAMPercentage=70`이 한도가 없을 때 호스트 전체 메모리를
기준으로 삼아 JVM 3개가 각각 70%를 잡는다.

**고친 파일을 지금 커밋하지 않는다.** `main`에 푸시되는 순간 `Deploy`가 돌면서 아직 세팅이 끝나지
않은 호스트를 향해 배포를 시도한다 — 맨 위 "실행 순서"가 "실패하는 것은 첫 실행 하나뿐"이라고
약속한 것이 깨진다. 수정한 파일은 **Step 8의 `scp`로 호스트에 올려서** 이 런북 동안 쓰고, 커밋은
Step 15에서 다른 결과와 함께 한 번에 한다. (Step 8의 `scp`는 로컬 작업 트리의 파일을 그대로
올리므로, 커밋하지 않아도 방금 고친 값이 반영된다.)

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

**이 스텝이 Step 10을 되돌릴 수 있게 만드는 유일한 장치다. 건너뛰지 않는다.**

백업 파일을 쓸 자리가 있어야 한다. Step 5가 `/opt/icuh/logs/public`까지 포함해 배포 디렉터리를
제대로 만들지만, 그 전에 최상위 `/opt/icuh`만 여기서 먼저 만들어 둔다 — 안 그러면 아래 리다이렉트가
"디렉터리 없음"으로 실패한다. (Step 5에서 다시 mkdir/chown을 실행해도 안전하다 — 이미 있는 디렉터리에
대한 재실행이라 아무 것도 깨지지 않는다.)

```bash
sudo mkdir -p /opt/icuh
sudo chown -R "$USER":"$USER" /opt/icuh
```

컨테이너 이름은 아래에서 **한 번만** 적는다. 나머지 명령은 모두 이 값(`$OLD`)을 그대로 쓴다 —
같은 이름을 두 군데 이상에 따로 적으면 한쪽만 고치고 다른 쪽을 놓치는 사고가 나기 때문에, 애초에
고칠 곳을 하나로 줄인다. `OLD`는 지금 이 셸 세션에만 남는 변수다 — 중간에 접속이 끊겼다가 다시
붙은 뒤에 이어서 진행한다면, Step 10을 실행하기 전에 이 블록부터 다시 실행해 `OLD`를 다시 설정한다.

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

이미지 태그만으로는 부족하다. Step 10의 `docker rm`은 컨테이너의 실행 설정 — 환경변수 십여 개, 포트
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

`icuh-platform:rollback`은 배포 워크플로가 도는 `docker image prune`에 지워지지 않는다 — 정리 대상이
`label=re.kr.icuh.project=drought-platform`으로 한정돼 있고, 그 라벨은 이 파이프라인이 빌드한
이미지에만 붙기 때문이다. (라벨 필터 없이 나이만 봤다면 Step 10의 `docker rm` 직후 참조가 끊긴
이 이미지가 첫 배포에서 곧바로 지워졌을 것이다.) 다만 사람이 직접 `docker system prune -a` 같은
명령을 실행하면 당연히 함께 지워지니, 롤백 가능성이 남아 있는 동안에는 그런 명령을 쓰지 않는다.

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
`docker logs icuh-admin-api` / `docker logs icuh-open-api`로 본다. **거꾸로 `public-api`는 prod에서
표준출력에 배너 말고는 아무것도 내지 않는다**(`logback-prod.xml`에 콘솔 appender가 없다) — 그래서
`public-api`의 기동 실패는 `docker compose logs`가 아니라 `/opt/icuh/logs/public/platform.log`에서
읽는다. 자세한 내용은 `deploy/README.md`의 "로그" 절 참고.

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

- [ ] **Step 7: 배포할 커밋 SHA를 한 번만 정한다**

이 SHA는 Step 8(pull) · Step 10(기동) · Step 12(`.last-good` 기록)에서 **모두 같은 값**이어야 한다.
그래서 여기서 한 번만 정하고 이후에는 `$TAG`로만 쓴다. `latest`를 쓰면 안 된다 — `.last-good`에는
SHA가 들어가는데 실제로 떠 있는 컨테이너는 `latest`가 가리키던 무언가가 되어, 기록된 포인터와 도는
이미지가 처음부터 어긋난다(워크플로도 SHA만 배포하도록 되어 있고, 40자 SHA가 아닌 `image_tag`는
거부한다).

로컬 저장소에서 — "실행 순서" 2번의 `build-and-push`가 성공시킨 바로 그 커밋이다:

```bash
git fetch origin main
git rev-parse origin/main
```

EC2에서:

```bash
# 위에서 나온 40자 SHA를 그대로 붙여넣는다.
TAG=<그 SHA>

# 오타 가드. 40자 16진수가 아니면 여기서 걸러진다.
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] && echo "TAG OK: $TAG" || echo "실패: 40자 커밋 SHA가 아니다."
```

`TAG`는 `OLD`와 마찬가지로 **이 셸 세션에만 남는 변수다.** 접속이 끊겼다가 다시 붙었다면 Step 8이나
Step 10으로 넘어가기 전에 이 블록부터 다시 실행한다.

실행 결과:

```
(정한 커밋 SHA와 "TAG OK" 출력)

```

- [ ] **Step 8: compose 파일을 배치하고 이미지를 미리 받아본다**

로컬에서 — 커밋 여부와 무관하게 **작업 트리의 파일이 그대로 올라간다.** Step 1에서 `mem_limit`을
줄였다면 그 값이 이 `scp`로 반영된다:

```bash
scp -i <키> deploy/docker-compose.yml <user>@<host>:/opt/icuh/docker-compose.yml
```

EC2에서:

```bash
cd /opt/icuh
echo <GitHub PAT(read:packages)> | docker login ghcr.io -u <github-id> --password-stdin
: "${TAG:?Step 7의 TAG가 설정돼 있지 않다. 재접속했다면 Step 7을 다시 실행한다.}" \
  && IMAGE_TAG="$TAG" docker compose pull
```

Expected: 3개 이미지(`public-api` · `admin-api` · `open-api`)가 받아진다. 실패하면 ① `$TAG`가 정말
`build-and-push`가 푸시한 커밋인지, ② PAT 권한(`read:packages`), ③ GHCR 패키지의 visibility/Actions
access 설정을 차례로 의심한다.

**여기서는 `docker logout`을 하지 않는다.** Step 12의 `.last-good` 검증 pull이 이 로그인 세션을
그대로 쓰기 때문이다. 로그아웃은 Step 12 끝에서 한다.

실행 결과:

```
(docker compose pull 출력)

```

- [ ] **Step 9: 구 프로젝트의 배포 워크플로를 비활성화한다**

**Step 10 전에 한다.** 지금 8081을 쓰고 있는 구 앱은 구 저장소(`icuh-platform`)의 Actions 워크플로가
`develop` 브랜치 push마다 EC2에 다시 배포한다. 그대로 두면 Step 10에서 구 컨테이너를 지운 뒤에도
누군가 `develop`에 push하는 순간 구 앱이 8081에 되살아나, 새 `public-api`와 같은 포트를 두고 충돌한다.
컷오버 도중에 그 일이 벌어지지 않도록 **먼저** 끈다.

GitHub → 구 저장소(`icuh-platform`) → Actions → 좌측 워크플로 목록에서 EC2로 배포하는 워크플로 선택
→ 우측 `...` → **Disable workflow**.

되돌릴 일이 생기면 같은 자리에서 Enable로 다시 켤 수 있다. 비활성화는 이 문서 "먼저 읽는다" 절의
수동 롤백(`docker run ... icuh-platform:rollback`)에는 아무 영향이 없다 — 그 절차는 워크플로가 아니라
EC2에서 직접 도는 명령이다.

실행 결과:

```
(비활성화한 저장소·워크플로 이름과, Actions 화면에 Disabled로 표시되는 것을 확인했다는 기록)

```

- [ ] **Step 10: 구 컨테이너를 내리고 신 앱을 올린다**

**여기가 파괴적 단계다.** 컨테이너 이름은 Step 4에서 설정한 `$OLD`를, 이미지 태그는 Step 7에서
설정한 `$TAG`를 그대로 쓴다 — 여기서 새로 적지 않는다(같은 값을 두 곳에 따로 적으면 한쪽만 고치는
사고가 날 수 있어서다. `icuh_platform`이 이전 워크플로가 쓰던 이름을 그대로 옮겨 적은 추측값이라는
점과, 그 값을 어디서 확정하는지는 Step 4 참고).

아래는 한 덩어리로 붙여넣는 명령 **하나**다. 가드부터 재시작 확인까지 전부 `&&`로 묶여 있어, 중간
어느 지점이 실패하든 그 뒤는 전혀 실행되지 않고 곧바로 `중단됨` 메시지가 출력된다 — 블록을 둘로
나눠서 사이에 확인만 끼워 넣으면 그 경계에서 제어 흐름이 새어 나가기 때문에, 하나의 체인으로만
안전하다. `OLD`나 `TAG`가 이 셸 세션에 남아 있지 않으면(재접속 등으로 사라졌으면) 맨 앞의 가드가
실패한다. **가드가 셸 자체를 멈추는 것은 아니다** — `&&`는 대화형이든 비대화형이든 동일하게 동작해서,
가드가 실패한 순간 그 뒤에 연결된 `docker stop`·`docker rm`·재시작 확인·`docker compose up`·
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
아무 것도 실행되지 않았다. `docker compose ps`에 `public-api` · `admin-api` · `open-api` 세
서비스가 `Up`으로 보이고 `중단됨`이 나오지 않았으면 성공이다. 문제가 생기면 바로 위 "먼저 읽는다"
절의 되돌리기 명령을 쓴다 — Step 4의 백업이 없으면 되돌릴 수 없다.

실행 결과:

```
(docker compose ps 출력)

```

- [ ] **Step 11: 헬스체크로 확인한다**

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
정보 문제다. 단, **`public-api`는 `docker compose logs`에 아무것도 남기지 않는다** — 파일 로그만
쓰므로 `tail -100 /opt/icuh/logs/public/platform.log`를 본다(Step 5 설명 참고).

**되돌리려면:** `docker compose down && docker run -d --name icuh_platform -p 8081:8081 icuh-platform:rollback`
— 이 명령 자체는 최소 기동만 한다. 원래 포트/환경변수/볼륨 옵션은 Step 4에서 남긴
`/opt/icuh/old-container-inspect.json` · `/opt/icuh/old-container-env.txt`를 보고 반영한다. 두 파일이
없다면 참고용으로 `public-api/.github/workflows/cicd.yml`의 Deploy 스텝을 대신 본다.

실행 결과:

```
(세 curl 명령의 출력과 종료 여부)

```

- [ ] **Step 12: 첫 성공 태그를 기록하고, 그 태그가 실재하는 이미지인지 확인한다**

`.last-good`은 배포 워크플로가 자동 롤백할 때 되돌릴 대상으로 읽는 유일한 파일이다. 여기에 적힌
태그로 이미지를 **실제로 받을 수 없으면 자동 롤백은 그 순간 실패한다** — 그래서 기록하고 끝내지 않고
곧바로 pull로 검증한다. Step 7~10과 같은 `$TAG`를 쓴다.

EC2에서:

```bash
cd /opt/icuh
: "${TAG:?Step 7의 TAG가 설정돼 있지 않다. 재접속했다면 Step 7을 다시 실행한다.}" \
  && echo "$TAG" > /opt/icuh/.last-good \
  && grep -Eq '^[0-9a-f]{40}$' /opt/icuh/.last-good \
  && IMAGE_TAG=$(cat /opt/icuh/.last-good) docker compose pull \
  && echo ".last-good 검증 OK — 이 태그로 자동 롤백이 가능하다" \
  || echo "실패: .last-good이 40자 SHA가 아니거나, 그 태그의 이미지를 받을 수 없다. 자동 롤백이 불가능한 상태이니 여기서 멈추고 원인을 찾는다."
```

`.last-good 검증 OK`가 나와야 다음으로 넘어간다. 나오지 않으면 워크플로의 자동 롤백은 있으나 마나다.

검증이 끝났으면 이제 GHCR에서 로그아웃한다(Step 8에서 미뤄 둔 것이다). PAT가 호스트의
`~/.docker/config.json`에 남지 않게 한다:

```bash
docker logout ghcr.io
```

실행 결과:

```
(기록한 커밋 SHA, ".last-good 검증 OK" 여부, docker logout 출력)

```

- [ ] **Step 13: 보안그룹을 정리한다**

AWS 콘솔 → EC2 → 보안그룹 → 인바운드 규칙:

- 8081 — 이미 열려 있다 (구 앱이 쓰던 것). 유지.
- **8083 — 새로 추가한다.** 소스는 프론트가 접근할 범위로.
- **8082 — 추가하지 않는다.** `admin-api`는 인증이 없다. (승인·반려·병합 엔드포인트까지 전부
  무방비로 열려 있다는 뜻이다.) 접근이 필요하면 보안그룹을 열지 말고 SSH 터널을 쓴다 —
  방법은 `deploy/README.md`의 "admin-api 접근" 절 참고.
- 22 — Actions가 배포 때 임시로 열고 닫으므로 상시 규칙은 두지 않는다(관리자 접속용 고정 IP 규칙은
  별개로 유지해도 된다).

**이 보안그룹의 ID(`sg-...`)를 적어 둔다.** Step 14의 `AWS_SG_ID`가 정확히 이 값이어야 한다.

실행 결과:

```
(정리 후 인바운드 규칙 스냅샷 — 8081/8083 존재, 8082 부재, 22 상시 규칙 부재를 확인했다고 기록)
(이 인스턴스의 보안그룹 ID)

```

- [ ] **Step 14: 이 저장소의 GitHub Secrets를 확인한다**

`deploy` 워크플로는 아래 6개 시크릿을 쓴다. 하나라도 비어 있거나 엉뚱한 곳을 가리키면 배포가
실패하거나 — 더 나쁘게 — **엉뚱한 호스트/보안그룹에 배포한다.**

GitHub → 이 저장소 → Settings → Secrets and variables → Actions:

| 이름 | 값 |
|---|---|
| `SSH_EC2_KEY` | 이 EC2 접속용 개인키 전체 (`-----BEGIN ...` 줄부터 끝줄까지) |
| `SSH_EC2_USER` | Step 1에서 SSH로 접속할 때 쓴 `<user>` |
| `SSH_EC2_HOST` | Step 1에서 접속한 바로 그 `<host>` |
| `AWS_ACCESS_KEY_ID` | 보안그룹 규칙을 추가/삭제할 수 있는 IAM 사용자 |
| `AWS_SECRET_ACCESS_KEY` | 위 사용자의 시크릿 키 |
| `AWS_SG_ID` | **Step 13에서 적어 둔 보안그룹 ID** |

**주의 — 값이 이미 있어도 그대로 믿지 않는다.** 이 여섯 이름은 모듈별 구 워크플로
(`*/.github/workflows/cicd.yml`)가 쓰던 이름과 같다. 그래서 이 저장소에 **이미 값이 들어 있을 수
있고, 그 값이 이전 프로젝트의 호스트나 보안그룹을 가리킬 수 있다.** 이름이 있다는 것은 값이 맞다는
뜻이 아니다.

게다가 GitHub Secrets는 **한 번 저장하면 값을 다시 읽을 수 없다.** 화면에서 대조해 확인하는 방법이
없다는 뜻이다. 그러므로 "확인"은 눈으로 보는 것이 아니라 **여섯 개를 모두 지금 아는 값으로 다시 저장하는
것**이다 (Update 버튼으로 덮어쓴다). 특히 `SSH_EC2_HOST`와 `AWS_SG_ID` 두 개는 반드시 덮어쓴다 —
이 둘이 틀리면 배포가 다른 인스턴스로 가거나, 엉뚱한 보안그룹의 22번 포트를 열었다 닫는다.

실행 결과:

```
(여섯 개 시크릿을 각각 언제/어떤 근거의 값으로 저장했는지 기록. 값 자체는 여기 적지 않는다.)

```

- [ ] **Step 15: 런북 결과를 커밋하고, Deploy를 수동 실행해 마무리한다**

**이 런북에서 커밋하는 지점은 여기 하나뿐이다.** 중간에 커밋하면 `main` 푸시가 `Deploy`를 깨워
아직 세팅 중인 호스트로 배포가 나간다. Step 1에서 `mem_limit`을 조정했다면 그 변경도 여기서 함께
커밋한다(런북 동안에는 Step 8의 `scp`로만 반영돼 있었다).

위 각 스텝의 "실행 결과" 슬롯을 모두 채운 뒤:

```bash
git add deploy/RUNBOOK.md
git add deploy/docker-compose.yml   # Step 1에서 mem_limit을 조정했을 때만
git commit -m "docs: EC2 최초 세팅 런북과 실행 결과 기록"
```

그리고 문서 맨 위 "실행 순서" 4번을 실행한다 — Actions → Deploy → Run workflow(`main`, `image_tag`
비움). 이 실행이 끝까지 통과해야 파이프라인 전체가 실제로 동작한다는 것이 확인된다. (이 커밋을
`main`에 푸시했다면 그 push로도 `Deploy`가 한 번 돈다 — 그 실행이 통과했다면 수동 실행은 생략해도
된다.)

> 이 문서를 저장소에 처음 추가하는 커밋(본 작업)은 위 절차를 아직 아무것도 실행하지 않은 상태로
> 만들어졌으므로, "실행 결과" 슬롯이 비어 있다. 실제 EC2 세팅을 수행한 뒤 이 커밋 메시지로 결과를
> 채워 다시 커밋하는 것은 운영자의 몫이다.

실행 결과:

```
(수동 실행한 Deploy run의 링크와 결과)

```

---

## 브리프와 저장소가 어긋나는 부분

- 되돌리기 안내(Step 11)가 가리키는 워크플로 파일 경로는 원문에서 `icuh-platform/.github/workflows/cicd.yml`이지만,
  현재 저장소에는 이 경로가 없다. 동일한 내용(구 컨테이너 실행 옵션)은 `public-api/.github/workflows/cicd.yml`의
  `Deploy to EC2` 스텝에 있다 — 위 두 곳 모두 이 경로로 고쳐 적었다.
- 헬스체크(Step 11)의 응답 본문은 태스크 안내에서 "public-api는 빈 본문, 나머지 둘은 `OK`"라고 주어졌으나,
  세 `HealthController` 소스를 직접 확인한 결과는 반대다 — `public-api`·`admin-api`가 문자열 `OK`를 반환하고
  (`public-api/src/main/java/.../health/api/HealthController.java`, `admin-api/src/main/java/.../HealthController.java`),
  `open-api`가 `ResponseEntity.build()`로 빈 본문을 반환한다
  (`open-api/src/main/java/.../HealthController.java`). 이 문서는 소스 코드 기준(반대)으로 작성했다.
- Step 4는 원래 이미지 태그(`icuh-platform:rollback`)만 백업했다. 이미지만으로는 부족하다 —
  컷오버 스텝(Step 10)의 `docker rm`이 구 컨테이너의 실행 설정(환경변수 십여 개, 포트 매핑, 볼륨
  마운트)까지 함께 지우기 때문이다. 그래서 Step 4에 `docker inspect` 전체 출력과 환경변수 목록을
  파일로 남기는 절차를 추가했다(`/opt/icuh/old-container-inspect.json` ·
  `/opt/icuh/old-container-env.txt`). 뒤의 파일에는 구 앱의 DB 비밀번호·AWS 시크릿 키가 평문으로
  들어가므로 `chmod 600`으로 권한을 좁히고, 롤백 가능성이 없어지면 삭제하도록 문서에 명시했다.
  이 캡처가 `/opt/icuh` 존재를 전제하므로, Step 5의 디렉터리 생성 중 최상위 `/opt/icuh` 부분만
  Step 4로 앞당겼다.
- 브리프 원문의 컷오버 스텝은 `docker stop icuh_platform && docker rm icuh_platform`처럼 추측 컨테이너
  이름을 그 자리에 직접 적었다. 이 스텝(현재 Step 10)을 안전하게 고치는 데 세 번을 거쳤다. ① 이름을
  두 곳(백업 캡처와 이 명령)에 따로 적으면 운영자가 한쪽만 고치고 다른 쪽을 놓칠 수 있어, Step 4에서
  설정한 `$OLD`를 재사용하도록 바꿨다. ② `: "${OLD:?...}"` 가드를 독립된 줄로 두었더니, 그 형태는
  비대화형 셸에서만 전체를 중단시키고, 대화형 SSH 세션에 블록을 붙여넣는 실제 상황에서는 가드가
  실패 메시지만 내고 다음 줄로 그대로 넘어갔다 — 그래서 가드를 `&&`로 `docker stop`·`docker rm`에
  체인으로 묶었다. ③ 그런데 그 체인을 재시작 확인과 별도 블록으로 나눴더니, 그 경계에서 제어 흐름이
  새어 나갔다 — `docker compose up -d`가 확인 결과와 무관하게 무조건 실행됐고, 심지어 `OLD`가 비어
  있으면 `--filter "name=^/${OLD}$"`가 아무 것도 매치하지 않아 `grep -q .`가 실패해 `||` 분기가
  "제거 확인 — 신규 컨테이너를 올린다"라는 거짓 안전 신호까지 냈다. 최종적으로 가드부터
  `docker compose ps`까지 전부 하나의 `&&` 체인으로 묶고, 맨 끝에 `|| echo "중단됨..."`을 붙여 어느
  지점에서 실패하든 그 뒤 전부가 건너뛰어지고 실패가 눈에 보이도록 했다.
- 스텝 번호는 브리프 원문(12스텝)과 더 이상 1:1로 맞지 않는다. 최종 리뷰에서 세 가지가 빠져 있다는
  것이 드러나 스텝을 셋 추가했기 때문이다 — 배포 SHA를 한 번만 확정하는 Step 7(그 전에는 Step 7·8이
  `latest`로 띄우면서 `.last-good`에는 SHA를 적어, 기록된 포인터와 도는 이미지가 처음부터
  어긋났다), 구 저장소 워크플로를 끄는 Step 9, GitHub Secrets를 확인하는 Step 14다. 문서 안의 모든
  스텝 상호 참조는 새 번호로 맞춰 두었다.
