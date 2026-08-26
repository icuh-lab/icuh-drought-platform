# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

인프라재난관리 문서 공유 플랫폼의 백엔드. 사용자가 문서(게시글 + 첨부파일)를 업로드하면 승인 절차를 거쳐 공개되는 구조. Spring Boot 3.4.4 · Java 17 · MySQL + JPA + QueryDSL · AWS S3.

## 개발 명령어

```bash
# 전체 빌드 (테스트 포함)
./gradlew build

# 테스트 없이 빌드 (CI/Docker와 동일)
./gradlew build -x test

# 로컬 실행 (기본 프로필: local, 포트 8081)
./gradlew bootRun

# 전체 테스트
./gradlew test

# 단일 테스트 클래스 / 메서드
./gradlew test --tests "re.kr.icuh.drought.publicapi.IcuhPlatformApplicationTests"
./gradlew test --tests "*ArticleServiceTest*"
./gradlew test --tests "*ArticleServiceTest.단일_메서드명"
```

- **QueryDSL Q-클래스는 빌드 시 `annotationProcessor`가 `build/generated/`에 생성한다.** 엔티티(`@Entity`)를 추가·수정하면 `QXxx` 클래스가 갱신되도록 한 번 빌드해야 컴파일이 통과한다.
- `lint` 도구는 없다. 코드 스타일은 주변 코드에 맞춘다.

## 실행 전 필수 설정

- **프로필**: `application.yml`에서 `local`이 기본, `secret`이 항상 include 된다. `prod`는 배포 시 `SPRING_PROFILES_ACTIVE=prod`로 활성화.
- **설정 파일**: `application-secret.yml`, `application-prod.yml`은 **커밋되어 있다** — 값이 전부 `${...}` 환경변수 자리표시자라서 커밋해도 안전하고, CI가 만드는 이미지에 들어가려면 추적되어야 한다. 로컬/운영 실행에는 그 자리표시자에 대응하는 환경변수가 필요하다.
- **로컬 환경변수**: `LOCAL_DB_URL`, `LOCAL_DB_USERNAME`, `LOCAL_DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, 그리고 S3용 `spring.cloud.aws.*` 자격증명.
- **`ddl-auto: none`** — 스키마를 자동 생성하지 않는다. DB 테이블은 외부에서 관리되므로, 엔티티 필드/테이블을 바꾸면 실제 스키마도 수동으로 맞춰야 한다.

## 아키텍처

### 패키지 구조 — 도메인 기반 (최근 3-tier에서 이관)

각 도메인은 아래 레이어로 분리된다:

```
{domain}/
  api/          # @RestController — HTTP 진입점, ApiResponse로 감싸 반환
  application/  # @Service — 트랜잭션 경계, 비즈니스 조합
  domain/       # @Entity + enum — 도메인 로직이 엔티티 안에 들어있음 (rich domain model)
  infra/        # Spring Data Repository (+ QueryDSL 커스텀 구현)
  dto/request, dto/response/
```

도메인: `article`(게시글) · `category`(분류 참조데이터) · `file`(첨부파일/S3) · `health`. 횡단 관심사는 `global/`(`common`, `config`)에 모여 있다.

호출 흐름은 **Controller → Service → Repository** 단방향. `article` 서비스가 `category`/`file` 리포지토리를 직접 참조하는 등, 도메인 간 협력은 서비스 계층에서 이뤄진다.

### 공통 응답·예외 규약 (`global/common`)

- **모든 응답은 `ApiResponse<T>`로 감싼다.** `ApiResponse.success(data)` / `.created(data)` / `.success()`(무바디) 정적 팩터리를 사용. 컨트롤러는 이 타입을 그대로 반환한다.
- **에러 처리 파이프라인**: 서비스에서 `throw new BusinessException(ErrorCode.XXX)` → `GlobalExceptionHandler`(`@RestControllerAdvice`)가 잡아 `ApiResponse.error(...)`로 변환. 새 에러는 반드시 **`ErrorCode` enum에 한 곳으로** 추가한다(상태코드·코드·한글 메시지를 함께 정의).
- **페이지네이션**: `Page<T>`는 `PageResponse.from(page)`로 감싸 반환. 컨트롤러는 `Pageable` + `@PageableDefault`/`@SortDefault`를 받는다.

### QueryDSL 동적 쿼리 패턴

동적 조건 조회는 3-파일 패턴을 따른다:

- `XxxRepository extends JpaRepository<Xxx, Long>, XxxRepositoryCustom`
- `XxxRepositoryCustom` — 커스텀 메서드 인터페이스
- `XxxRepositoryImpl implements XxxRepositoryCustom` — `JPAQueryFactory`(`QuerydslConfig` 빈) + `BooleanBuilder`로 구현

`ArticleRepositoryImpl.findApprovedArticles`가 기준 예시다. **연관 엔티티는 `fetchJoin()`으로 함께 로딩해 N+1을 방지한다**(최근 이 부분을 리팩터링함). 카운트 쿼리는 별도로 만들어 `PageableExecutionUtils.getPage`에 넘긴다.

### 도메인별 핵심 로직 (읽어야 알 수 있는 부분)

- **Article — 승인 워크플로우 + 소프트 삭제**: `ArticleStatus` enum(`PENDING`/`APPROVED`/`REJECTED`/`DELETED`/`UPDATED_PENDING`/`UPDATED_APPROVED`/`DELETED_PENDING`)으로 상태를 관리. 목록 조회는 `APPROVED`/`UPDATED_APPROVED`만 노출한다. `delete()`는 실제 삭제가 아니라 상태를 `DELETED_PENDING`으로 바꾼다.
  - **수정은 즉시 반영되지 않는다.** `updateArticle`은 변경 내용을 엔티티의 `pendingUpdate` 컬럼에 **JSON으로 스테이징**한다(`UpdateArticleRequestJsonConverter` `AttributeConverter`가 `UpdateArticleRequest` ↔ JSON 변환). 승인 시 반영되는 구조.
  - **비밀번호(`tempPassword`)는 SHA-256 해시로 저장**되며 `validatePassword`로 검증한다. 삭제·상태변경 시 비밀번호 확인이 필요.
  - 도메인 메서드(`increaseViews`, `delete`, `updateContent`, `validatePassword`)가 엔티티에 있다. 상태 변경은 setter가 아니라 이 메서드들을 통한다.
- **File — S3 멀티파트 업로드 (Presigned URL 방식)**: `FileController`가 S3와 직접 통신한다. 흐름: `generate-upload-id`(멀티파트 개시) → `presigned-url`(파트별 PUT용 URL, 만료 10분) → `complete-upload`/`update-upload`(파트 조합 완료). 다운로드는 S3 객체를 스트림으로 내려준다. **AWS SDK는 v1(`com.amazonaws.*` `AmazonS3Client`)** 을 쓴다. 버킷명은 `spring.cloud.aws.s3.bucket`. `FileEntity`는 `Article`에 `@ManyToOne`으로 연결되고 Article 저장 시 함께 관리된다.
- **Category — 참조 데이터**: `DocumentType`(문서 성격), `SubjectDomain`(주제 영역). `code`로 조회하는 읽기 전용 룩업 테이블이며 게시글 분류에 사용된다.
- **통합 등록 API**: `POST /api/v1/articles-with-files`가 게시글 + 첨부 메타데이터를 한 트랜잭션에 저장한다(S3 업로드는 이 호출 전에 이미 끝나 있어야 함).

## 컨벤션

- **언어**: 커밋 메시지·주석·PR/이슈 템플릿·에러 메시지 모두 한글.
- **커밋**: `feat:`, `fix:`, `refactor:`, `chore:` 등 타입 프리픽스 + 한글 설명.
- **브랜치**: `{type}/#{issue}/{설명}` 형식 (예: `feat/#87/create-category-api`). PR은 **`develop` 브랜치로** 머지한다(`main` 아님).
- **DTO**: 요청/응답은 record 사용 (`request.documentType()` 접근자 스타일). 엔티티→응답 변환은 정적 팩터리(`XxxResponse.fromEntity` / `.of`).
- **엔티티**: `@NoArgsConstructor(access = PROTECTED)` + 생성자 위 `@Builder`. 무분별한 setter 대신 의미 있는 도메인 메서드를 추가한다.

## AI 에이전트 안전 규칙 (작업 전 필독)

이 규칙들은 코드가 암묵적으로 요구하는 가드레일이다. 어기면 데이터 유실·정보 노출·런타임 장애로 직결된다.

### 검증
작업 완료를 선언하기 전 아래 게이트를 통과시킨다. **CI는 테스트를 실행하지 않으며**(`build -x test`), 유일한 테스트 `contextLoads()`는 전체 `@SpringBootTest`라 AWS 자격증명·DB 환경변수(`AWS_ACCESS_KEY_ID` 등) 없이는 컨텍스트 로드에 실패한다 → **시크릿 없는 환경(클린 로컬/CI)에는 실행 가능한 자동 안전망이 없다.**

```bash
# 0. Gradle 데몬은 JDK 17로 실행 (Gradle 8.13은 JDK 24+에서 태스크 생성이 깨짐 — "Type T not present")
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 1. (필수) 컴파일 + 테스트소스 + QueryDSL Q-클래스 재생성 — 시크릿 불필요
./gradlew clean compileJava compileTestJava --console=plain

# 2. (필수) CI/Docker 동등 빌드 — 시크릿 불필요
./gradlew build -x test --console=plain

# 3. (로직 변경 시, env 있을 때만) 테스트 실행
./gradlew test --console=plain
```

- 테스트를 추가할 땐 **전체 `@SpringBootTest`를 지양**하고, 순수 단위 테스트나 `@DataJpaTest` + Testcontainers로 작성해 게이트 1~2(시크릿 없는 환경)에서 함께 돌아가게 한다.
- **린트 도구가 없으므로**(Checkstyle/Spotless 등 미설정) 스타일은 주변 코드에 수동으로 맞춘다. 별도 typecheck도 없다(Java 컴파일이 곧 타입체크).
- "`build -x test` 통과 = 검증 완료"로 간주하지 않는다. 게이트 3을 못 돌렸으면 검증 범위(예: "컴파일·어셈블만 검증, 런타임 테스트 미실행")를 정확히 보고한다.

### 스키마·데이터
- **`ddl-auto: none`** — 앱이 스키마를 만들지 않는다. 엔티티에 컬럼/테이블을 추가·변경하는 작업은 반드시 대응하는 DDL이 필요함을 사용자에게 알리고, 마이그레이션 없이 스키마 의존 변경을 단독으로 진행하지 않는다.
- **`Article.pendingUpdate`(JSON 컬럼)** 는 `UpdateArticleRequest` record를 그대로 직렬화한다. 이 record의 필드를 바꾸면 DB에 저장된 기존 JSON 역직렬화가 실패(`RuntimeException`)한다 → **하위호환을 깨는 변경**으로 취급하고 마이그레이션을 동반한다.

### 도메인 불변식
- **엔티티 상태는 도메인 메서드로만 변경한다.** 엔티티에 `@Setter`를 추가하지 말고 `@NoArgsConstructor(access = PROTECTED)`를 유지한다. `tempPassword`는 빌더에서 SHA-256으로 해싱되므로, 우회 생성/평문 저장 금지, 비밀번호 비교는 반드시 `validatePassword()`를 통한다.
- **게시글·파일은 물리 삭제 금지.** `deleteById`/`delete(entity)` 등 물리 삭제를 쓰지 말고 상태 전이(`Article.delete()` → `DELETED_PENDING`)를 사용한다. ⚠️ 현재 `Article.delete()`는 `status`만 바꾸고 `isDeleted`/`deletedAt`은 갱신하지 않는다(삭제 표식이 이원화됨) — 삭제 관련 로직을 수정할 땐 이 둘의 관계부터 확인한다.
- **공개 조회 쿼리에는 승인 상태 필터를 반드시 포함한다.** 게시글 목록/검색 쿼리는 `APPROVED`/`UPDATED_APPROVED`만 노출해야 한다(`findApprovedArticles` 기준). 새 조회 쿼리에서 이 필터를 빠뜨리면 미승인·삭제대기 글이 노출된다.

### 쿼리
- QueryDSL에서 연관 엔티티는 `fetchJoin`으로 N+1을 막되, **`@OneToMany` 컬렉션(예: `Article.files`)은 페이징 쿼리에서 `fetchJoin`하지 않는다** — Hibernate가 메모리 페이징으로 전환되어 성능이 붕괴한다. 컬렉션은 batch size 또는 별도 조회로 처리한다.

### 시크릿·외부 연동
- `application-secret.yml`·`application-prod.yml`은 **추적되는 파일이지만, 값은 반드시 `${...}` 환경변수 자리표시자로만 둔다.** 리터럴 자격증명(DB 비밀번호, AWS 키, 버킷 이름 등)을 이 파일이나 다른 코드에 하드코딩하지 않는다 — 실제 값은 EC2의 `/opt/icuh/.env.<name>`(600)에만 존재한다. 자리표시자가 아닌 값을 넣어야 할 상황이면 커밋하지 말고 사용자에게 먼저 알린다.
- S3 연동은 **AWS SDK v1(`com.amazonaws.*`)** 을 쓴다. 새 S3 코드는 기존 `AmazonS3Client`에 맞추고, v2 혼용이나 마이그레이션은 사용자 합의 후에만 진행한다.

### 응답 규약
- 새 컨트롤러 응답은 `ApiResponse<T>`로 래핑한다. **기존 `CategoryController`/`HealthController`는 래핑하지 않은 예외 케이스이며, 이 방식을 새 코드에서 답습하지 않는다.**

## CI/CD

`.github/workflows/cicd.yml` — `develop`으로 push/PR 시 실행. `./gradlew build -x test` → Docker 이미지 빌드/푸시(Docker Hub) → EC2에 SSH로 배포(`prod` 프로필, 포트 8081). 배포 단계에서 GitHub Actions IP를 보안그룹에 한시적으로 열었다가 닫는다.
