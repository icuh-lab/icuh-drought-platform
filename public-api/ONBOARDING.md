# 온보딩 가이드

새로 합류한 팀원이 이 저장소를 처음 열었을 때 읽는 문서입니다.
코드 컨벤션과 AI 에이전트 작업 규칙은 [`CLAUDE.md`](./CLAUDE.md), 용어 사전은 [`README.md`](./README.md)에 있습니다.

---

## 1. 프로젝트 개요

**인프라재난관리 분야의 문서 공유 플랫폼 백엔드**입니다. 누구나 문서(게시글 + 첨부파일)를 올릴 수 있지만, 관리자 승인을 거쳐야만 공개됩니다.

로그인·회원 개념이 없는 대신 업로더가 **임시 비밀번호**를 걸어 본인을 증명하고, 대용량 첨부는 서버를 거치지 않고 **브라우저에서 S3로 직접 멀티파트 업로드**합니다. 즉 이 프로젝트가 푸는 문제는 *"인증 시스템 없이 신원 확인과 품질 게이트를 동시에 세우는 것"* 과 *"큰 파일을 서버 메모리 밖에서 처리하는 것"* 두 가지입니다.

**스택**: Spring Boot 3.4.4 · Java 17 · MySQL + JPA + QueryDSL 5.0 · AWS S3(**SDK v1**) · Gradle 8.13 · Docker → EC2

---

## 2. 디렉토리 구조

### 최상위

| 경로 | 역할 |
|---|---|
| `src/main/java/re/kr/icuh/icuhplatform/` | 애플리케이션 코드 전부 |
| `src/main/resources/` | 프로필별 설정(`application-*.yml`), logback 설정 |
| `src/test/java/` | 테스트 (대부분 순수 단위 테스트) |
| `build.gradle` | 의존성·빌드 설정 |
| `Dockerfile` | 멀티스테이지 빌드 (builder: JDK17 → runner: JRE17) |
| `.github/workflows/cicd.yml` | `develop` 브랜치 CI/CD |
| `CLAUDE.md` | 코딩 컨벤션 + 작업 안전 규칙 (**작업 전 필독**) |

### 패키지 — 도메인 기반 4계층

```
re/kr/icuh/icuhplatform/
├── IcuhPlatformApplication.java   ← 진입점 (main)
│
├── article/       ★ 핵심 도메인 — 게시글 + 승인 워크플로우
├── file/            첨부파일 + S3 멀티파트 업로드
├── category/        분류 참조데이터 (읽기 전용 룩업)
├── health/          헬스체크 (GET /health)
│
└── global/
    ├── common/      ApiResponse · ErrorCode · BusinessException
    │                GlobalExceptionHandler · PageResponse · FileUtils
    └── config/      CorsConfig · QuerydslConfig · S3Config
```

`article` · `file` · `category`는 모두 **같은 4계층**으로 쪼개집니다:

| 폴더 | 역할 | 이 폴더에 넣을 것 / 넣지 말 것 |
|---|---|---|
| `api/` | `@RestController`. HTTP 진입점 | 얇게 유지. 비즈니스 판단을 넣지 않습니다 |
| `application/` | `@Service`. **트랜잭션 경계**, 유스케이스 조합 | 도메인 간 협력은 반드시 여기서 |
| `domain/` | `@Entity` + enum. **도메인 규칙이 사는 곳** | setter 금지. 의미 있는 메서드로 상태를 바꿉니다 |
| `infra/` | Spring Data Repository + QueryDSL 구현 | 동적 쿼리는 3-파일 패턴 (아래 참고) |
| `dto/request`, `dto/response` | record DTO | 엔티티 → 응답 변환은 정적 팩터리(`fromEntity`/`of`) |

**호출 방향은 단방향입니다: Controller → Service → Repository → Domain.** 역방향 참조가 생기면 설계가 어긋난 신호입니다.

**QueryDSL 3-파일 패턴** (동적 조건 조회를 추가할 때):

```
XxxRepository        extends JpaRepository<Xxx, Long>, XxxRepositoryCustom
XxxRepositoryCustom  ← 커스텀 메서드 인터페이스
XxxRepositoryImpl    ← JPAQueryFactory + BooleanBuilder로 구현
```

---

## 3. 핵심 파일 5개

중요도 순입니다. 이 5개만 읽으면 제품의 80%가 이해됩니다.

| # | 파일 | 역할 |
|---|---|---|
| 1 | `core-persistence` 모듈 `persistence/article/entity/Article.java` (Task 4에서 이동) | 승인 상태 전이·비밀번호 해싱/검증·소프트 삭제·수정 스테이징까지 **모든 도메인 규칙이 실제로 강제되는 유일한 곳** |
| 2 | `article/application/ArticleService.java` | 트랜잭션 경계를 잡고 비밀번호 게이트를 통과시킨 뒤 게시글+파일을 원자적으로 저장하는 **유스케이스 오케스트레이터** |
| 3 | `article/infra/ArticleRepositoryImpl.java` | 동적 검색 조건을 조립하면서 승인 상태 필터를 거는 **공개 노출 경계** — 미승인 글 유출을 막는 방어선 |
| 4 | `file/api/FileController.java` | S3 멀티파트 업로드 4단계 **프로토콜 전체**를 직접 구현 |
| 5 | `core-persistence` 모듈 `persistence/article/converter/UpdateArticleRequestJsonConverter.java` (Task 3에서 이동) | 수정 요청 record ↔ `pending_update` JSON 컬럼 변환. **"수정은 승인 전까지 반영하지 않는다"는 규칙을 성립시키는 장치** |

> ⚠️ 5번 파일 주의: `UpdateArticleRequest` record(`core-domain` 모듈 `domain/article/UpdateArticleRequest.java`, Task 3에서 이동)의 필드를 바꾸면 **DB에 이미 저장된 JSON의 역직렬화가 실패**합니다. 하위호환을 깨는 변경으로 취급하고 마이그레이션을 동반하세요.

### 꼭 알아야 할 도메인 개념

**`ArticleStatus`(`core-domain` 모듈 `domain/article/ArticleStatus.java`, Task 3에서 이동) 7개 상태로 라이프사이클 전체를 표현합니다:**

```
등록 → PENDING ─승인→ APPROVED ─수정요청→ UPDATED_PENDING → UPDATED_APPROVED
          └─거절→ REJECTED                     └─삭제요청→ DELETED_PENDING → DELETED
```

여기서 파생되는 세 가지 불변식:

1. **공개 목록에는 `APPROVED` / `UPDATED_APPROVED`만 노출합니다.** 새 조회 쿼리를 만들 때 이 필터를 빠뜨리면 미승인 글이 새어 나갑니다.
2. **물리 삭제 금지.** `deleteById`를 쓰지 말고 `Article.delete()`(→ `DELETED_PENDING`)를 씁니다.
3. **수정은 즉시 반영되지 않습니다.** 본문을 덮어쓰지 않고 `pendingUpdate` 컬럼에 JSON으로 쌓아둡니다.

---

## 4. 주요 코드 흐름

### 공통 파이프라인 — 모든 요청이 지나는 길

```
클라이언트 → Tomcat(:8081) → DispatcherServlet
  → ① CORS 검사 (CorsConfig)
  → ② HandlerMapping (URL+메서드 → 컨트롤러)
  → ③ ArgumentResolver (@RequestBody 역직렬화 / @Valid 검증 / Pageable)
  → ④ 컨트롤러
  → ⑤ @Transactional 프록시 → 서비스 → 리포지토리 → 도메인
  → ⑥ 커밋 (dirty checking flush)
  → ⑦ ApiResponse<T> JSON 직렬화 → 클라이언트
```

> 이 파이프라인에는 **커스텀 필터·인터셉터·AOP·Spring Security가 하나도 없습니다.** 요청은 CORS만 통과하면 곧장 컨트롤러에 닿고, 신원 확인은 서비스 메서드 안의 비밀번호 비교뿐입니다.

### 흐름 A. 문서 등록 — 요청 4개의 시퀀스

이 프로젝트에서 가장 중요한 흐름입니다. 하나의 요청이 아닙니다.

```
[1] POST /api/v1/multipart-upload/generate-upload-id
      FileUtils가 "{UUID}.확장자" 저장명 생성 → S3 멀티파트 개시
      ← { uploadId, s3Key }

[2] POST /api/v1/multipart-upload/presigned-url          (파트 수만큼 반복)
      만료 10분짜리 PUT용 presigned URL 발급
      ← URL 문자열

    ★ 브라우저가 이 URL로 S3에 직접 PUT — 서버를 거치지 않습니다.
      각 파트의 ETag를 브라우저가 수집합니다.

[3] POST /api/v1/multipart-upload/complete-upload
      ETag 목록으로 S3가 파트를 하나로 조립
      ⚠️ DB에 저장하지 않고 메타데이터만 응답으로 돌려줍니다.

[4] POST /api/v1/articles-with-files                     ← 여기서만 DB에 씁니다
      @Valid 검증 → documentTypeCode / subjectDomainCode 존재 확인
      → Article 빌더 (tempPassword가 SHA-256 해싱됨, status=PENDING)
      → INSERT articles + INSERT files (한 트랜잭션)
      ← { "status": 200, "data": { "id": 123 } }
```

**왜 이렇게 설계했나**: 파일을 먼저 다 올리고 마지막 `[4]`에서만 DB에 쓰기 때문에, 업로드가 중간에 실패하면 DB에 아무 흔적도 남지 않습니다. **고아 파일 레코드가 구조적으로 생길 수 없습니다.**

### 흐름 B. 목록 조회 — `GET /api/v1/articles`

```
ArticleController.findArticles
  → ArticleService.findArticles          @Transactional(readOnly = true)
  → ArticleRepositoryImpl.findApprovedArticles
       ├─ BooleanBuilder: null이 아닌 파라미터만 AND 누적
       │    documentType / subjectDomain / source / query(제목 LIKE)
       ├─ 🔒 승인 필터를 항상 주입 (APPROVED OR UPDATED_APPROVED)
       ├─ documentType·subjectDomain은 fetchJoin → N+1 없음
       │    ※ files 컬렉션은 일부러 fetchJoin 안 함 (메모리 페이징 방지)
       └─ 카운트 쿼리 분리 → PageableExecutionUtils가 필요할 때만 실행
  → ArticleListResponse::fromEntity → PageResponse.from → ApiResponse.success
```

### 흐름 C. 비밀번호 게이트 — 삭제 / 상태 변경

```
DELETE /api/v1/articles/{id}   → validatePassword → Article.delete()  (DELETED_PENDING)
POST   /api/v1/articles/{id}   → validatePassword → Article.reject()  (PENDING만 → REJECTED)
PATCH  /api/v1/articles/{id}   → Article.updateContent()              (pendingUpdate에 JSON 스테이징)
```

비밀번호는 입력값을 SHA-256으로 해싱해 저장된 해시와 비교하고, 불일치 시 `INVALID_PASSWORD` → **401**입니다.

### 흐름 D. 에러 — 예외는 전부 한 곳으로 모입니다

```
서비스/도메인:  throw new BusinessException(ErrorCode.XXX)
  → 트랜잭션 롤백 (RuntimeException)
  → GlobalExceptionHandler (@RestControllerAdvice)
       ├─ BusinessException              → ErrorCode의 status/code/message 사용
       ├─ BindException                  → 400 INVALID_INPUT
       │    (@Valid 실패 시 나오는 MethodArgumentNotValidException이 여기 걸립니다)
       ├─ MaxUploadSizeExceededException → 413
       └─ Exception (그 외)               → 500 (상세 내용 비노출)
  → ApiResponse.error(...)
```

```json
{ "status": 401, "message": "비밀번호가 일치하지 않습니다.",
  "error": { "code": "INVALID_PASSWORD", "details": "비밀번호가 일치하지 않습니다." } }
```

**새 에러를 추가할 때는 반드시 `ErrorCode` enum 한 곳에만** 추가합니다(상태코드·코드·한글 메시지를 함께). `ErrorCodeTest`가 "code 문자열 == enum 상수명" 불변식을 강제하므로 어기면 테스트가 깨집니다.

---

## 5. 개발 시작하기

### 5.1 사전 준비

- **JDK 17** (필수 — Gradle 8.13은 JDK 24+에서 태스크 생성이 깨집니다)
- MySQL 접속 정보
- AWS S3 자격증명 + 버킷

### 5.2 설정 파일 — 이게 없으면 앱이 뜨지 않습니다

`src/main/resources/`의 설정 파일 중 **2개는 git에 없습니다.** 팀에서 따로 받아야 합니다.

| 파일 | git | 비고 |
|---|---|---|
| `application.yml` | ✅ 추적됨 | 기본 프로필 `local`, `secret`을 항상 include |
| `application-local.yml` | ✅ 추적됨 | 환경변수 참조만 있고 실제 값은 없음 |
| `logging.yml` | ✅ 추적됨 | 프로필별 logback 설정을 가리킴 |
| **`application-secret.yml`** | ❌ **gitignore** | **팀에서 받으세요** (AWS 자격증명) |
| **`application-prod.yml`** | ❌ **gitignore** | 배포용 |

> 🔒 이 두 파일은 절대 커밋하지 마세요. `git add -f`로 강제 추가하지 않습니다.

**필요한 환경변수** (`application-local.yml`이 참조):

```bash
export LOCAL_DB_URL=jdbc:mysql://localhost:3306/{DB명}
export LOCAL_DB_USERNAME={사용자}
export LOCAL_DB_PASSWORD={비밀번호}
export CORS_ALLOWED_ORIGINS=http://localhost:3000    # 콤마로 여러 개 가능
```

**코드가 요구하는 프로퍼티** (`application-secret.yml`이 채워야 함):

```
spring.cloud.aws.credentials.access-key
spring.cloud.aws.credentials.secret-key
spring.cloud.aws.region.static
spring.cloud.aws.s3.bucket
```

> ⚠️ **`ddl-auto: none`** 입니다. 앱이 테이블을 만들지 않습니다. DB 스키마는 외부에서 관리되므로 **엔티티 필드/테이블을 바꾸면 실제 DDL도 수동으로 맞춰야 합니다.**

### 5.3 실행

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

./gradlew bootRun          # 로컬 실행 (프로필 local, 포트 8081)
curl localhost:8081/health # → OK
```

> QueryDSL `QXxx` 클래스는 빌드 시 `annotationProcessor`가 `build/generated/`에 생성합니다.
> `@Entity`를 추가·수정하면 **한 번 빌드해야** 컴파일이 통과합니다.

### 5.4 빌드 & 테스트 — 작업 완료 전 이 3개를 통과시키세요

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 게이트 1: 컴파일 + QueryDSL Q-클래스 재생성 (시크릿 불필요)
./gradlew clean compileJava compileTestJava --console=plain

# 게이트 2: CI/Docker와 동일한 빌드 (시크릿 불필요)
./gradlew build -x test --console=plain

# 게이트 3: 단위 테스트 (시크릿 불필요, 26개)
./gradlew test --console=plain \
  --tests "*ArticleTest" --tests "*ArticleServiceTest" \
  --tests "*FileUtilsTest" --tests "*ErrorCodeTest" \
  --tests "*CorsConfigTest" --tests "*FileResponseTest"
```

**테스트 구성**

| 테스트 | 성격 | 시크릿 필요 |
|---|---|---|
| `ArticleTest` | 순수 단위 (도메인 상태 전이) | ❌ |
| `ArticleServiceTest` | Mockito 단위 | ❌ |
| `FileUtilsTest` · `ErrorCodeTest` · `CorsConfigTest` · `FileResponseTest` | 순수 단위 | ❌ |
| `IcuhPlatformApplicationTests` | `@SpringBootTest` (컨텍스트 로드) | ✅ **AWS·DB 환경변수 필요** |

- `./gradlew test`를 인자 없이 돌리면 `IcuhPlatformApplicationTests`가 포함되어 **시크릿 없는 환경에서는 실패**합니다. 위 게이트 3처럼 클래스를 지정하세요.
- **CI는 테스트를 실행하지 않습니다** (`build -x test`). 즉 자동 안전망이 얇으니, 로컬에서 게이트를 돌리는 습관이 중요합니다.
- 새 테스트는 **전체 `@SpringBootTest`를 지양**하고 순수 단위 테스트 또는 `@DataJpaTest`로 작성해, 시크릿 없는 환경에서도 돌아가게 해주세요.
- **린트 도구가 없습니다** (Checkstyle/Spotless 미설정). 스타일은 주변 코드에 수동으로 맞춥니다.

### 5.5 브랜치 & 커밋 컨벤션

```
브랜치:  {type}/#{issue}/{설명}     예) feat/#87/create-category-api
커밋:    feat: / fix: / refactor: / chore: + 한글 설명
PR 대상: develop  (main 아님)
```

커밋 메시지·주석·에러 메시지·PR 템플릿은 **모두 한글**로 씁니다.

### 5.6 배포

`develop`에 push/PR → GitHub Actions가 `./gradlew build -x test` → Docker 이미지 빌드/푸시(Docker Hub) → EC2에 SSH 배포(`prod` 프로필, 8081). 배포 중 Actions IP를 보안그룹에 한시적으로 열었다 닫습니다.

---

## 6. 지금 알아야 할 미완성 / 주의 지점

코드를 읽다가 "왜 이러지?" 하고 멈추게 되는 지점들입니다. 버그가 아니라 **아직 안 만든 것**과 **실제 결함**이 섞여 있으니 구분해서 보세요.

### 미완성 (설계는 있으나 구현 전)

- **승인 경로가 없습니다.** `modifyArticleStatus`는 이름과 달리 `reject()`만 호출합니다. `APPROVED` / `UPDATED_APPROVED` / `DELETED`로 가는 전이 코드가 없고, `pendingUpdate`에 쌓인 JSON을 본문에 반영하는 코드도 없습니다. **상태 머신의 절반이 미구현입니다.**
- `[3] complete-upload` 후 `[4]`를 보내지 않으면 **S3에 고아 객체가 남습니다.** 정리 로직(lifecycle rule / abort 배치)이 없습니다.
- 거절 사유(`reason`)는 `log.info`로만 찍히고 저장되지 않습니다.

### 실제 결함 (건드릴 일이 있으면 같이 고쳐주세요)

- **`PATCH /articles/{id}`에 비밀번호 검증이 없습니다.** `UpdateArticleRequest`가 `tempPassword`를 `@NotNull`로 받아놓고 한 번도 비교하지 않습니다. 인증 계층이 없는 앱에서 이 경로만 무방비입니다.
- **`findArticleById`에 상태 필터가 없습니다.** ID만 알면 `PENDING`·`REJECTED`·`DELETED_PENDING` 글도 본문·첨부까지 조회되고 조회수도 올라갑니다.
- `@SortDefault`를 받아놓고 `ArticleRepositoryImpl`이 `orderBy(createdAt.desc())`를 하드코딩합니다. `?sort=views,desc`는 **조용히 무시됩니다.**
- `ModifyArticleStatusRequest` / `DeleteArticleRequest`에 `@NotNull`이 없어, `password`를 생략하면 400이어야 할 것이 **NPE로 500**이 됩니다.
- 비밀번호가 **salt 없는 단일 라운드 SHA-256**입니다. 임시 비밀번호가 짧으면 사실상 무방비입니다.
- `Article.java`가 `org.testcontainers.shaded...Hashing`을 import합니다 — 그래서 테스트 라이브러리(`testcontainers:localstack`)가 `implementation` 스코프에 들어가 있습니다. **프로덕션 코드가 테스트 라이브러리에 의존하는 상태**입니다.
- `FileController`가 서비스 계층을 건너뛰고 `AmazonS3Client`를 직접 호출합니다. 다른 도메인의 계층 규칙과 어긋납니다.
- 생성 API가 `ApiResponse.created()`(201)가 아니라 `success()`(200)를 반환합니다.
- `CategoryController` / `HealthController`는 `ApiResponse`로 감싸지 않는 **예외 케이스**입니다. 새 코드에서 이 방식을 따라하지 마세요.
