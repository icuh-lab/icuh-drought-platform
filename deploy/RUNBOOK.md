# EC2 최초 세팅 런북

> **상태: 2026-08-27 실행 완료. 다만 이 문서의 전제가 실측과 달라, 절차 원문을 그대로 따르면 안 된다.**
> 무엇이 어떻게 달랐고 지금 EC2가 어떤 상태인지는 아래 **"실행 완료 기록"** 절에 있다. 특히 **롤백
> 절차가 바뀌었다** — "먼저 읽는다" 절과 Step 4·11의 되돌리기 명령은 존재하지 않는 파일과 이미지를
> 가리키므로 실패한다. 각 스텝의 "실행 결과"에 실제로 무엇을 했는지 적어 두었고, 절차 원문은 설계
> 의도를 남기려고 지우지 않았다.

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

> **[2026-08-27] 이 단락의 조건은 충족됐다.** `/opt/icuh/.last-good`에
> `91d0c9b24a546a6fc4eae903709add451e2bc92d`가 기록돼 있고 그 태그로 세 컨테이너가 실제로 떠 있다.
> **워크플로의 자동 롤백은 지금 성립한다.** 반면 이 단락이 "그 전에 쓰라"고 안내한 수동 절차 쪽이
> 오히려 무효가 됐다(Step 4를 원문대로 실행하지 않았다) — 대체 절차는 "실행 완료 기록" 절에 있다.

---

## 실행 완료 기록 — 2026-08-27

**이 런북은 2026-08-27에 실행됐다. 그런데 이 문서의 전제가 실측과 달랐다.**

런북은 "8081에서 서비스 중인 구 컨테이너 `icuh_platform`을 멈추고 그 자리를 `public-api`로 **교체**한다"를
전제로 쓰였다(Step 4·10, "먼저 읽는다" 절 전체). 실제로 EC2에 붙어 보니 **`icuh_platform`은 이미 4일 전에
종료(`Exited (143)`)된 상태였고, 8081을 점유하고 있지 않았다.** 살아서 트래픽을 받고 있던 것은 런북이
언급조차 하지 않은 **8080의 `icuh_platform_api`**였다.

그래서 컷오버는 일어나지 않았다. **교체가 아니라 병행이 됐다.** 구 API는 8080에서 계속 돌고, 신규 3개
앱이 8081·8082·8083에 새로 올라갔다. 이 문서에서 가장 위험하다고 경고한 Step 10(파괴적 컷오버)은
**실행되지 않았고, 구 컨테이너는 하나도 삭제되지 않았다.**

### 최종 토폴로지

호스트: `ec2-54-180-165-127.ap-northeast-2.compute.amazonaws.com` (ubuntu, 3907MB)

| 포트 | 컨테이너 | 이미지 | 외부 노출 |
|---|---|---|---|
| 8080 | `icuh_platform_api` | `ljs0429777/icuh-platform-api:latest` | **열림** — 구 API, 계속 운영 |
| 8081 | `icuh-public-api` | `.../public-api:91d0c9b2…` | **열림** — 구 앱이 쓰던 보안그룹 규칙이 남아 자동으로 열려 있었다 |
| 8082 | `icuh-admin-api` | `.../admin-api:91d0c9b2…` | **차단** — 보안그룹 미개방 + `127.0.0.1` 바인딩 이중 차단 |
| 8083 | `icuh-open-api` | `.../open-api:91d0c9b2…` | **미개방** — Step 13 지시와 달리 열지 않았다. 아래 참고 |

신규 3개는 모두 커밋 `91d0c9b24a546a6fc4eae903709add451e2bc92d` 태그로 떠 있고, 헬스체크
8081·8082·8083 모두 200이다(8080 구 API도 200).

> **[이후 변경] 위 표는 배포 직후(2026-08-27 오전) 상태다.** 같은 날 Caddy 리버스 프록시 도입을
> 시작하면서 8081·8083이 **호스트 루프백 바인딩으로 바뀌었고**(`127.0.0.1:8081` / `127.0.0.1:8083`),
> 두 컨테이너가 `web` 도커 네트워크에도 붙었다. 외부 트래픽은 Caddy의 80·443만 받는다.
> 최종 주소는 `https://api.infradna.io.kr`(public) · `https://open-api.infradna.io.kr`(open)이다.
> 자세한 내용은 `deploy/README.md`를 본다.

### 스텝별 실제 처리

| 스텝 | 처리 |
|---|---|
| 1 | 실행. 다만 여기서 전제가 무너진 것을 발견했다 |
| 2·3 | **생략** — 메모리 3907MB(2GB 이상), compose v2.40.0 이미 설치됨 |
| 4 | **다르게 처리** — 컷오버를 안 하기로 해서 `icuh-platform:rollback` 태그와 두 백업 파일을 만들지 않았다. 아래 "롤백 전제가 바뀌었다" 참고 |
| 5·6 | 실행 |
| 7·8·10·11·12 | **Deploy 워크플로 실행으로 대체** — 사람이 SSH로 하지 않았다. run [33029320575](https://github.com/icuh-lab/icuh-drought-platform/actions/runs/33029320575) (`workflow_dispatch`, `main`, 2026-08-27T01:11:52Z UTC, 2m50s, success) |
| 9 | 실행. 단 구 저장소가 하나가 아니라 넷이었다 — Step 9 슬롯 참고 |
| 13 | **생략** — 8083을 열지 않았다. 8081은 이미 열려 있었고 8082는 원래 안 여는 것이라, 실제로 할 일이 없었다 |
| 14·15 | 실행 |

### 롤백 전제가 바뀌었다 — "먼저 읽는다" 절보다 이쪽이 맞다

문서 앞쪽 "먼저 읽는다" 절과 Step 4·11이 안내하는 되돌리기 명령은
`docker run … icuh-platform:rollback`과 `/opt/icuh/old-container-inspect.json` ·
`/opt/icuh/old-container-env.txt`를 쓴다. **그 이미지 태그도, 그 두 파일도 EC2에 없다.** 컷오버를
하지 않기로 하면서 Step 4를 그 형태로 실행하지 않았기 때문이다. 그 명령을 그대로 치면 실패한다.

대신 **더 온전한 롤백 수단이 남았다.** Step 10의 `docker rm`을 실행하지 않았으므로 구 컨테이너들이
실행 설정을 그대로 지닌 채 `exited` 상태로 살아 있다 — 환경변수·포트 매핑·볼륨이 도커 안에 원형
그대로 보존돼 있다는 뜻이다.

```bash
docker inspect --format '{{.Name}} {{.State.Status}} {{.HostConfig.PortBindings}}' icuh_platform icuh_platform_admin
# /icuh_platform       exited map[8081/tcp:[{ 8081}]]
# /icuh_platform_admin exited map[8082/tcp:[{ 8082}]]
```

되돌리려면 `docker run`으로 새로 만드는 것이 아니라 **`docker start`로 되살린다.** 다만 두 가지를
반드시 먼저 안다.

1. **8081은 지금 `icuh-public-api`가 점유하고 있다.** `docker start icuh_platform`을 그냥 실행하면
   포트 충돌로 실패한다. 되돌릴 때는 `cd /opt/icuh && docker compose down`을 **먼저** 한다.
2. **`icuh_platform_admin`은 `0.0.0.0:8082`에 바인딩돼 있다.** 되살리는 순간 인증이 없는 구 admin이
   외부에 그대로 열린다. 신규 `admin-api`를 루프백에 가둔 이유와 정확히 같은 위험이니, 이 컨테이너는
   되살리지 않는다.

```bash
# 구 앱으로 되돌리기 (8081)
cd /opt/icuh && docker compose down && docker start icuh_platform && docker ps
```

**따라서 지금은 구 컨테이너 5개를 지우지 않는 것 자체가 롤백 장치다.** `docker rm`이나
`docker system prune -a`를 실행하지 않는다. 배포 워크플로의 `docker image prune`은
`label=re.kr.icuh.project=drought-platform`으로 한정돼 있어 구 이미지를 건드리지 않는다.

### 이 문서를 다시 쓸 사람에게 — 남은 위험

- **`icuh-lab/icuh-platform-fo`의 CI/CD 워크플로가 켜져 있고, `-p 80:80`으로 배포한다.** 컨테이너는
  지금 exited지만 누가 push하면 되살아난다. 프론트를 Caddy(https)로 올릴 때 **80번 포트를 두고
  충돌한다.** 그 작업 전에 이 워크플로를 끄거나 구 FO를 정리한다.
- **8083을 아직 열지 않아 프론트가 open-api를 호출할 수 없다.** 게다가 프론트는 Caddy로 https를 쓰는데
  API는 raw http라, 열더라도 https 페이지에서의 호출은 브라우저가 mixed content로 차단한다. Caddy가
  API도 함께 프록시하는 방향으로 합의했고, 그때 **8083은 Caddy 서버 IP에만 열고 8081은 오히려 닫는다.**
- **`admin-api`는 여전히 인증이 전혀 없다.** 지금은 이중 차단으로 가려 둔 것뿐이고, 노출하려면 인증이
  선행되어야 한다.
- **구 API(8080)를 언제까지 병행할지 정해지지 않았다.** 이 컨테이너에는 `mem_limit`이 없어 호스트
  전체(3.8GiB)를 자기 몫으로 본다 — 신규 3개는 합계 1792m로 묶여 있는 것과 대조된다.
- **`open-api/outputs/db_check_work/실측가뭄_사례검증_DB현황조사_20260805.md`가 EC2를 `3.37.105.126`으로
  적고 있다.** 위 호스트와 다르다. 별개 인스턴스인지 IP 변경인지 확인되지 않았다.

---

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

> **[2026-08-27 실행 후] 이 절의 되돌리기 명령은 그대로 쓰면 실패한다.** 아래 네 항목은 "8081의 구
> 컨테이너를 지우고 교체한다"는 전제로 쓰였는데, 실제 실행에서는 컷오버를 하지 않았다. 그래서
> 여기 나오는 `icuh-platform:rollback` 이미지도, `/opt/icuh/old-container-inspect.json` ·
> `old-container-env.txt` 두 파일도 **EC2에 존재하지 않는다.** 지금 유효한 롤백 절차는 위
> "실행 완료 기록 → 롤백 전제가 바뀌었다"에 있다(`docker compose down && docker start icuh_platform`).
> 아래 원문은 절차가 어떤 전제로 설계됐는지 남겨 두려고 그대로 둔다.

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

- [x] **Step 1: 현재 상태를 기록한다**

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

**여기서 이 런북의 전제가 무너졌다.** 아래는 같은 날 실측으로 재확인한 값이다(실행 당시 원본 출력은
그대로 보존하지 못했다).

```
$ docker ps -a
icuh_platform_api     ljs0429777/icuh-platform-api:latest    0.0.0.0:8080->8080/tcp   Up 4 days
icuh_platform         ljs0429777/icuh-platform:latest        (포트 없음)              Exited (143) 4 days ago
icuh_platform_admin   ljs0429777/icuh-platform-admin:latest  0.0.0.0:8082->8082/tcp   Exited (255) 4 days ago
icuh-platform-fo      ljs0429777/icuh-platform-fo:latest     (포트 없음)              Exited (0) 4 days ago
icuh_platform_api_before_dbfix_20260821141622                (포트 없음)              Exited (143) 5 days ago
eloquent_wilbur       hello-world                            (포트 없음)              Exited (0) 10 months ago

$ docker compose version
Docker Compose version v2.40.0

$ free -m
               total        used        free      shared  buff/cache   available
Mem:            3907        1711         213           2        2289        2196
Swap:              0           0           0

$ swapon --show
(출력 없음 — SWAP-NONE)

$ df -h /
(보존하지 못함. 이후 배포·이미지 pull이 모두 성공했으므로 여유는 있었다)
```

읽어야 할 것 셋:

- **`icuh_platform`이 `Exited (143)` 상태이고 포트가 없다.** 이 런북이 "8081에서 서비스 중"이라
  전제하고 Step 4·10을 통째로 설계한 그 컨테이너다. 이미 4일 전에 죽어 있었다. 8081은 비어 있었다.
- **살아 있던 것은 `icuh_platform_api`(8080)다.** 이 문서는 이 컨테이너를 한 번도 언급하지 않는다.
  즉 컷오버 대상이 애초에 존재하지 않았고, 신규 배포는 교체가 아니라 **병행**이 된다.
- 메모리 3907MB, compose v2.40.0 → **Step 2·3은 건너뛴다.** `mem_limit` 합계 1792m에 OS·도커 몫
  512m을 더해도 2304m로 3907m 안에 들어오므로 `docker-compose.yml`은 손대지 않았다(Step 15에서
  커밋할 compose 변경도 따라서 없다).

- [x] **Step 2: (메모리가 2GB 미만일 때만) 스왑을 만든다** — *건너뜀*

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
N/A — 메모리 충분(3907MB). 건너뛰었다. 스왑은 지금도 0이다.

```

- [x] **Step 3: docker compose v2가 없으면 설치한다** — *건너뜀*

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
N/A — 이미 compose v2 있음(v2.40.0). 건너뛰었다.

```

- [x] **Step 4: 구 앱 이미지와 실행 설정을 백업한다 (되돌릴 수 있게)** — *아래 원문대로 실행하지 않았다*

> **[2026-08-27] 이 스텝은 원문대로 실행되지 않았다.** Step 1에서 컷오버 대상(`icuh_platform`)이 이미
> 죽어 있는 것이 드러나 Step 10을 실행하지 않기로 했고, 그러자 이 백업의 목적 자체가 사라졌다.
> `icuh-platform:rollback` 태그도, 아래 두 파일도 만들지 않았다. 대신 `docker ps -a` 스냅샷만
> `/opt/icuh/old-containers.txt`로 남겼다. **구 컨테이너를 `docker rm`하지 않은 것이 결과적으로 더
> 온전한 롤백 수단이 됐다** — 자세한 내용은 스텝 끝의 실행 결과와 "실행 완료 기록" 절 참고.

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

위 명령들은 실행하지 않았다. 실제로 남긴 것과, 지금 유효한 롤백 수단은 다음과 같다.

```
$ ls -l /opt/icuh/old-containers.txt
-rw------- 1 ubuntu ubuntu 541 Aug 27 10:02 /opt/icuh/old-containers.txt   # docker ps -a 스냅샷 6줄

$ ls -l /opt/icuh/old-container-inspect.json /opt/icuh/old-container-env.txt
ls: cannot access ...: No such file or directory                            # 둘 다 만들지 않았다

$ docker images | grep -i rollback
icuh-platform-api   rollback   dc859e0cdfd0   2 months ago   358MB          # ← 이 런북과 무관한 태그다

$ docker inspect icuh-platform:rollback
Error: No such object: icuh-platform:rollback                               # Step 4가 만들었을 태그는 없다
```

> **함정 주의.** `docker images | grep rollback`을 치면 `icuh-platform-api:rollback`이 걸린다.
> 이름이 비슷해서 "Step 4가 실행됐구나"로 오해하기 쉽지만, **이건 2개월 전에 만들어진 8080 구
> API(`icuh-platform-api`)의 태그이고 이 런북과 아무 관계가 없다.** Step 4가 만들었어야 할 태그는
> `icuh-platform:rollback`(`-api` 없음)이며, 그것은 존재하지 않는다.

- `old-containers.txt`에는 **시크릿이 없다**(`password|secret|access_key|SPRING_DATASOURCE` 검사 0건).
  `docker inspect`의 환경변수 덤프가 아니라 `docker ps -a` 출력이기 때문이다. 원문 Step 4가 경고한
  "평문 시크릿이 든 파일을 나중에 반드시 지울 것"은 **해당 사항이 없다.**
- 대신 구 컨테이너 5개가 `docker rm`되지 않고 `exited` 상태로 남아, 실행 설정이 도커 안에 원형 그대로
  보존돼 있다. 파일로 백업할 필요 자체가 없어진 셈이다.

```
$ docker inspect --format '{{.Name}} {{.State.Status}} {{.HostConfig.PortBindings}}' icuh_platform icuh_platform_admin
/icuh_platform       exited map[8081/tcp:[{ 8081}]]
/icuh_platform_admin exited map[8082/tcp:[{ 8082}]]
```

컨테이너가 참조하는 이미지도 그대로 살아 있다 — `icuh_platform`은 `sha256:dbe2d0b1efcc`를 쓰고,
이 이미지는 `ljs0429777/icuh-platform:latest`로 태깅돼 있다. 즉 컨테이너·이미지 양쪽이 온전하다.

**그래서 지금은 "구 컨테이너를 지우지 않는 것" 자체가 롤백 장치다.** `docker rm`·`docker system prune -a`를
실행하지 않는다. 되돌리는 명령은 `docker run`이 아니라 `docker start`이며, 8081을 점유 중인 신규
컨테이너를 먼저 내려야 한다 — 정확한 절차와 `icuh_platform_admin`을 되살리면 안 되는 이유는
"실행 완료 기록 → 롤백 전제가 바뀌었다" 참고.

- [x] **Step 5: 배포 디렉터리를 만든다**

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

- [x] **Step 6: 서비스별 `.env.<name>`을 작성한다**

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
세 파일 모두 작성 완료(권한 600 확인).

$ ls -l /opt/icuh/.env.*
-rw------- 1 ubuntu ubuntu 677 Aug 27 10:06 /opt/icuh/.env.admin
-rw------- 1 ubuntu ubuntu 479 Aug 27 10:08 /opt/icuh/.env.open
-rw------- 1 ubuntu ubuntu 658 Aug 27 10:09 /opt/icuh/.env.public

값의 출처: `.example`의 CHANGEME를 새로 채운 것이 아니라, **구 컨테이너들의
`docker inspect` 환경변수에서 그대로 옮겼다.** 구 앱이 실제로 쓰던 값이라
운영 DB·S3에 그대로 붙는다. `admin`의 DB는 `open`과 동일한 `ACTUAL_DRGHT`를 쓴다.
(운영 RDS는 VPC 안이라 EC2를 bastion으로 경유해야 접속된다.)

```

- [x] **Step 7: 배포할 커밋 SHA를 한 번만 정한다** — *워크플로가 대체*

> **[2026-08-27] 이 스텝은 사람이 SSH로 실행하지 않았다.** Step 7·8·10·11·12는 `Deploy` 워크플로
> 실행(run [33029320575](https://github.com/icuh-lab/icuh-drought-platform/actions/runs/33029320575))이
> 동일한 일을 대신했다. 결과는 스텝 끝에 기록했다.


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
TAG = 91d0c9b24a546a6fc4eae903709add451e2bc92d  (main HEAD, 커밋 91d0c9b "Merge pull request #3")

셸 변수로 정하지 않았다. `Deploy`를 `image_tag` 비운 채 `main`에서 실행하면 워크플로가
`github.sha`를 태그로 잡으므로, 그 값이 그대로 이 자리의 TAG가 됐다. 아래 Step 8·10·12의
결과가 모두 같은 SHA인 것이 그 증거다.

```

- [x] **Step 8: compose 파일을 배치하고 이미지를 미리 받아본다** — *워크플로가 대체*

> **[2026-08-27] 이 스텝은 사람이 SSH로 실행하지 않았다.** Step 7·8·10·11·12는 `Deploy` 워크플로
> 실행(run [33029320575](https://github.com/icuh-lab/icuh-drought-platform/actions/runs/33029320575))이
> 동일한 일을 대신했다. 결과는 스텝 끝에 기록했다.


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
워크플로의 `Sync compose file` + `Pull and start` 스텝이 대신 수행했다.

**GHCR 로그인은 원문과 달리 사람의 PAT를 쓰지 않는다.** 워크플로의 `Log in to GHCR on EC2` 스텝이
EC2에서 `docker login ghcr.io -u ${{ github.actor }} --password-stdin`을 실행하되, 비밀번호로
**그 run에서만 유효한 `secrets.GITHUB_TOKEN`**을 넣는다. 원문 Step 8이 준비물로 요구한
`read:packages` PAT는 결과적으로 쓰이지 않았다.

세 이미지 모두 같은 태그로 받아졌음이 기동 결과로 확인된다:

$ docker ps --format '{{.Names}}\t{{.Image}}'
icuh-public-api   ghcr.io/icuh-lab/icuh-drought-platform/public-api:91d0c9b24a546a6fc4eae903709add451e2bc92d
icuh-admin-api    ghcr.io/icuh-lab/icuh-drought-platform/admin-api:91d0c9b24a546a6fc4eae903709add451e2bc92d
icuh-open-api     ghcr.io/icuh-lab/icuh-drought-platform/open-api:91d0c9b24a546a6fc4eae903709add451e2bc92d

`/opt/icuh/docker-compose.yml`도 저장소 파일과 동일한 2099바이트로 올라가 있다(mem_limit 무수정).

```

- [x] **Step 9: 구 프로젝트의 배포 워크플로를 비활성화한다** — *대상이 하나가 아니었다*

> **[2026-08-27] 구 저장소는 하나가 아니라 넷이다.** 이 스텝은 `icuh-platform` 하나만 가리키지만,
> 실제로는 `icuh-platform` · `icuh-platform-api` · `icuh-platform-admin` · `icuh-platform-fo`가
> 각각 자기 워크플로로 이 EC2에 배포한다. 스텝 끝에 넷을 모두 조사한 결과를 적었다.


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
`gh workflow list --all` 로 네 저장소를 모두 조사한 결과:

| 저장소 | 워크플로 | 상태 | 판단 |
|---|---|---|---|
| `icuh-lab/icuh-platform` | icuh-platform CI/CD flow | **disabled_manually** | 껐다. 원문 Step 9가 가리킨 대상 |
| `icuh-lab/icuh-platform-admin` | icuh-platform-admin CI/CD flow | **disabled_manually** | 껐다. 안 껐으면 인증 없는 구 admin이 `0.0.0.0:8082`로 되살아난다 |
| `icuh-lab/icuh-platform-api` | icuh-platform CD flow | **active (의도적으로 켜 둠)** | 8080 구 API를 병행 운영하므로 살려 둔다 |
| `icuh-lab/icuh-platform-fo` | icuh-platform-fo CI/CD | **active (미정리)** | 아래 경고 참고 |

- `icuh-platform-api`를 켜 둬도 신규 앱과 충돌하지 않는다. 그 `cicd.yml`의 배포 스텝이
  `docker stop icuh_platform_api && docker rm icuh_platform_api && docker run -d --name icuh_platform_api -p 8080:8080 ...`로
  **8080과 자기 컨테이너만** 건드리기 때문이다. 트리거는 `workflow_run`(CI 성공 후)이다.
- **`icuh-platform-fo`는 정리되지 않은 위험이다.** `docker run -d --name icuh-platform-fo -p 80:80 ...`으로
  **80번 포트**에 배포한다. 컨테이너는 지금 `Exited (0)`이지만 push 한 번이면 되살아나고, 프론트를
  Caddy(https)로 올릴 때 80번을 두고 충돌한다. 그 작업 전에 끄거나 구 FO를 정리할 것.

```

- [x] **Step 10: 구 컨테이너를 내리고 신 앱을 올린다** — *구 컨테이너는 내리지 않았다*

> **[2026-08-27] 이 스텝의 파괴적 절반은 실행되지 않았다.** 컷오버 대상이 이미 죽어 있었으므로
> `docker stop`·`docker rm`은 하지 않았고, 신규 3개를 올리는 `docker compose up`만 `Deploy`
> 워크플로가 수행했다. **구 컨테이너 5개는 지금도 `exited` 상태로 남아 있다** — 그것이 현재의
> 롤백 수단이다(Step 4 실행 결과 참고).


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
`docker stop`·`docker rm`은 실행하지 않았다. 신규 3개만 올라갔다.

$ docker ps --format '{{.Names}}\t{{.Ports}}\t{{.Status}}'
icuh-admin-api      127.0.0.1:8082->8082/tcp                        Up
icuh-public-api     0.0.0.0:8081->8081/tcp, [::]:8081->8081/tcp     Up
icuh-open-api       0.0.0.0:8083->8083/tcp, [::]:8083->8083/tcp     Up
icuh_platform_api   0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp     Up 4 days   ← 구 API, 그대로 둠

`admin-api`가 `127.0.0.1:8082`에만 바인딩된 것을 호스트에서도 확인했다:

$ ss -tlnp | grep -E '808[0-3]'
LISTEN 0.0.0.0:8080   LISTEN 0.0.0.0:8081   LISTEN 127.0.0.1:8082   LISTEN 0.0.0.0:8083

메모리 사용량(한도 대비):
icuh-public-api  244.5MiB / 768MiB (31.8%)
icuh-admin-api   244.2MiB / 512MiB (47.7%)
icuh-open-api    248.0MiB / 512MiB (48.4%)
icuh_platform_api 356.5MiB / 3.816GiB (9.1%)   ← 구 API에는 mem_limit이 없다. 호스트 전체를 자기 몫으로 본다.

```

- [x] **Step 11: 헬스체크로 확인한다** — *워크플로가 대체*

> **[2026-08-27] 이 스텝은 사람이 SSH로 실행하지 않았다.** Step 7·8·10·11·12는 `Deploy` 워크플로
> 실행(run [33029320575](https://github.com/icuh-lab/icuh-drought-platform/actions/runs/33029320575))이
> 동일한 일을 대신했다. 결과는 스텝 끝에 기록했다.


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
워크플로의 `Health check` 스텝이 통과했고, 이후 호스트에서도 직접 재확인했다.

$ curl -fsS -o /dev/null -w '%{http_code}' localhost:8081/health  -> 200
$ curl -fsS -o /dev/null -w '%{http_code}' localhost:8082/health  -> 200
$ curl -fsS -o /dev/null -w '%{http_code}' localhost:8083/health  -> 200
$ curl -fsS -o /dev/null -w '%{http_code}' localhost:8080/health  -> 200   (구 API, 참고)

`public-api`의 파일 로그도 정상적으로 쓰이고 있다:
$ ls -l /opt/icuh/logs/public/
-rw-r--r-- 1 root root 3556 Aug 27 10:14 platform.log

**되돌리기 안내 정정:** 이 스텝 본문의 `docker run ... icuh-platform:rollback` 명령은 지금 쓸 수 없다
(그 이미지 태그를 만들지 않았다). `cd /opt/icuh && docker compose down && docker start icuh_platform`을
쓴다 — "실행 완료 기록 → 롤백 전제가 바뀌었다" 참고.

```

- [x] **Step 12: 첫 성공 태그를 기록하고, 그 태그가 실재하는 이미지인지 확인한다** — *워크플로가 대체*

> **[2026-08-27] 이 스텝은 사람이 SSH로 실행하지 않았다.** Step 7·8·10·11·12는 `Deploy` 워크플로
> 실행(run [33029320575](https://github.com/icuh-lab/icuh-drought-platform/actions/runs/33029320575))이
> 동일한 일을 대신했다. 결과는 스텝 끝에 기록했다.


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
$ cat /opt/icuh/.last-good
91d0c9b24a546a6fc4eae903709add451e2bc92d

$ ls -l /opt/icuh/.last-good
-rw-rw-r-- 1 ubuntu ubuntu 41 Aug 27 10:14

워크플로의 마지막 스텝이 헬스체크 통과 후 이 파일을 갱신했다. 기록된 태그가 실제로 떠 있는 세
컨테이너의 이미지 태그와 **일치한다**(Step 8·10 결과 참고) — 원문이 pull로 검증하려던 조건이
기동 자체로 충족됐다. **이 시점부터 워크플로의 자동 롤백이 성립한다.**

`docker logout`도 워크플로가 대신 했다. `Log out of GHCR on EC2` 스텝이 `always()`로 걸려 있어
**배포가 실패한 run에서도** 실행된다(성공 스텝에 묶어 두면 실패 시 토큰이 호스트에 남는다는 것이
`deploy.yml`의 주석에 근거로 적혀 있다). 호스트에서 실제로 비어 있는 것을 확인했다:

$ ls -l ~/.docker/config.json
-rw------- 1 ubuntu ubuntu 103 Aug 27 10:14 /home/ubuntu/.docker/config.json
$ grep -o '"auths":[^}]*}' ~/.docker/config.json
(매치 없음 — 자격증명이 남아 있지 않다)

```

- [x] **Step 13: 보안그룹을 정리한다** — *8083은 열지 않았다*

> **[2026-08-27] 이 스텝은 사실상 생략됐다.** 8081은 구 앱 규칙이 남아 이미 열려 있었고, 8082는
> 원래 안 여는 것이며, **8083은 열 대상 IP가 아직 정해지지 않아 보류했다.** 결과적으로 추가한
> 규칙이 없다. 아래 원문의 "8083 — 새로 추가한다"는 아직 이행되지 않은 지시다.


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
**추가한 규칙 없음.** 원문 지시와 실제가 다르다.

| 포트 | 원문 지시 | 실제 |
|---|---|---|
| 8080 | (언급 없음) | **열림** — 구 API용. 이 문서가 예상하지 못한 항목이다 |
| 8081 | 이미 열려 있다, 유지 | 열림 유지 ✓ 구 앱이 쓰던 규칙이 그대로 남아 있었다 |
| 8082 | 추가하지 않는다 | 추가 안 함 ✓ `ss` 확인 결과 `127.0.0.1`에만 LISTEN |
| 8083 | **새로 추가한다** | **추가하지 않았다 (보류)** |

**8083을 보류한 이유:** 소스로 지정할 프론트 IP가 아직 없다. 프론트는 Caddy로 https를 쓸 예정인데
아직 미배포다. 게다가 API가 raw http라 8083을 열더라도 https 페이지에서의 호출은 브라우저가 mixed
content로 차단한다. **Caddy가 API도 함께 프록시하는 방향으로 합의했고, 그때 8083을 Caddy 서버 IP에만
열고 8081은 오히려 닫는다.** 즉 이 표의 최종 형태는 아직 확정되지 않았다.

보안그룹 ID: **미기록.** Step 14의 `AWS_SG_ID`가 유효하다는 것은 배포 성공으로 간접 확인됐지만
(22번 포트 open/close가 동작했다), ID 값 자체를 이 문서에 적어 두지 않았다. 다음에 콘솔에 들어갈 때 채운다.

```

- [x] **Step 14: 이 저장소의 GitHub Secrets를 확인한다**

`deploy` 워크플로는 아래 6개 시크릿을 쓴다. 하나라도 비어 있거나 엉뚱한 곳을 가리키면 배포가
실패하거나 — 더 나쁘게 — **엉뚱한 호스트/보안그룹에 배포한다.**

GitHub → 이 저장소 → Settings → Secrets and variables → Actions:

| 이름 | 값 |
|---|---|
| `SSH_EC2_KEY` | 개인키를 **base64로 인코딩한 한 줄**. pem 원문을 그대로 넣으면 안 된다 — 아래 참고 |

`SSH_EC2_KEY`는 반드시 이렇게 등록한다. 여러 줄 pem을 그대로 넣으면 등록 방법에
따라 줄바꿈이 사라져 배포가 `error in libcrypto`로 실패한다(실제로 두 번 겪었다).

```bash
base64 < ~/경로/키.pem | gh secret set SSH_EC2_KEY
```

워크플로가 `base64 -d`로 복원한 뒤 `ssh-keygen -y`로 유효성을 확인하므로,
잘못 등록하면 `Write SSH key` 단계에서 원인을 알려주며 멈춘다.
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
`SSH_EC2_KEY`에서 실제로 두 번 데였고, 그 결과가 커밋 `29ea172`(PR #3)다.

| 시크릿 | 상태 |
|---|---|
| `SSH_EC2_KEY` | **base64 한 줄로 재저장.** pem 원문을 넣었을 때 줄바꿈이 손상돼 `error in libcrypto`로 배포가 실패했다 |
| `SSH_EC2_USER` | `ubuntu` |
| `SSH_EC2_HOST` | `ec2-54-180-165-127.ap-northeast-2.compute.amazonaws.com` |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | 유효 — 22번 포트 open/close가 동작했다 |
| `AWS_SG_ID` | 유효 — 위와 같은 근거. 단 ID 값은 Step 13에 미기록 |

여섯 개가 모두 맞다는 것은 눈으로 대조해서가 아니라 **run 33029320575가 끝까지 통과한 것으로**
증명됐다. 실패한 두 번은 `SSH_EC2_KEY` 하나 때문이었다:

- run 33028012736 (`workflow_dispatch`, 19s, failure) — `Write SSH key`에서 키 손상
- run 33028206514 (push, 2m6s, failure) — 같은 원인

```

- [x] **Step 15: 런북 결과를 커밋하고, Deploy를 수동 실행해 마무리한다**

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

> ~~이 문서를 저장소에 처음 추가하는 커밋(본 작업)은 위 절차를 아직 아무것도 실행하지 않은 상태로
> 만들어졌으므로, "실행 결과" 슬롯이 비어 있다.~~
> **[2026-08-27] 슬롯을 모두 채웠다.** 다만 실행 당시의 원본 콘솔 출력은 보존하지 못해, 각 슬롯의
> 값은 배포 직후 같은 날 EC2에 다시 접속해 재확인한 실측이다(`docker ps` · `ss` · `curl` ·
> `cat .last-good` · `gh run list` · `gh workflow list`). 재확인할 수 없었던 항목(`df -h /`,
> 보안그룹 ID)은 슬롯에 "보존하지 못함" · "미기록"이라고 명시해 두었다 — 추정해서 채우지 않았다.

실행 결과:

```
**run [33029320575](https://github.com/icuh-lab/icuh-drought-platform/actions/runs/33029320575)**
— `workflow_dispatch`, `main`, `image_tag` 비움, 2026-08-27T01:11:52Z UTC 시작, 2m50s, **success**.

빌드 → GHCR 푸시 → SSH 배포 → 헬스체크 → `.last-good` 갱신까지 파이프라인 전체가 실제로 도는 것이
이 실행으로 확인됐다.

`docker-compose.yml` 변경은 없다(Step 1에서 `mem_limit` 조정이 불필요했다). 이 커밋은 런북 문서
하나만 담는다.

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
