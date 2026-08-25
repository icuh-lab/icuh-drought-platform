# 계획: public-api / admin-api 도메인 모델 통합

## 배경

`public-api`와 `admin-api`는 **같은 MySQL 스키마(`ACTUAL_DRGHT`)의 같은 테이블
(`articles`, `files`)** 을 각자의 JPA 엔티티로 두 벌 매핑하고 있다. 두 앱은
"문서 승인 워크플로우"라는 **하나의 상태 머신을 나눠 소유**한다.

- `public-api`: `PENDING` 생성, `REJECTED`, `DELETED_PENDING` 기록 / `APPROVED`·`UPDATED_APPROVED` 조회
- `admin-api`: `APPROVED`, `REJECTED`, `DELETED`, `UPDATED_APPROVED`로 전이

즉 두 개의 bounded context가 아니라 **한 컨텍스트에 대한 두 개의 진입점**이다.
따라서 모델은 한 벌로 합치고, 유스케이스(승인 워크플로우 vs 사용자 업로드)는
지금처럼 완전히 분리해 유지한다.

### 코드베이스에서 확인된 사실 (계획의 근거)

1. `ArticleStatus`, `FileStatus`, `UpdateArticleRequest`는 두 모듈에 있는 사본이
   **`package` 줄을 제외하면 100% 동일**하다. 의도된 컨텍스트별 모델링이 아니라 복붙이다.
2. `pending_update` JSON 컬럼이 두 앱의 **암묵적 계약**이다. public이 쓰고 admin이 읽는다.
   컨버터는 `@JsonTypeInfo` 없이 필드명 기반으로만 직렬화하므로
   **record의 패키지를 옮기는 것은 기존 DB 데이터에 영향이 없다.**
3. `softDelete()` 3개(`publicapi.file.domain.FileEntity`, `adminapi.core.domain.Article`,
   `adminapi.core.domain.FileEntity`)는 **호출부가 0개인 죽은 코드**다.
   살아 있는 삭제 경로는 `publicapi` `Article.delete()`(ArticleService:77)와
   admin의 `ApproveDeleteArticle`(`changeStatus(DELETED)`)뿐이다.
4. `adminapi` `FileEntity.setArticle()`은 `article = this.article;` — 파라미터에 필드를
   대입하는 no-op 버그다. 현재는 모든 호출부가 빌더에 `.article(this)`를 미리 넘겨서 가려져 있다.
5. 두 모듈의 `Article.sha256Encode`가 **`org.testcontainers.shaded.com.google.common.hash.Hashing`**
   (테스트 라이브러리에 shade된 Guava)로 프로덕션 비밀번호를 해싱한다.
   그래서 `testcontainers:localstack`이 `implementation` 스코프에 들어가 있다.
6. `FileEntity.builder()` 호출부는 전부 `.status(PENDING)`를 명시하거나(public),
   직후에 `changeStatus(APPROVED)`를 호출한다(admin). `Article.builder()`는 admin에서 쓰지 않는다.
7. `article_edit_requests`(`ArticleEditRequest`)와 `file_edit_request`(`FileEditRequest`)는
   **admin 전용 테이블**이다. 통합 대상이 아니다.

## Global Constraints

이 계획의 모든 태스크는 아래를 지킨다. 위반은 리뷰에서 defect다.

- **G1. DB 스키마를 변경하지 않는다.** `ddl-auto: none`이고 스키마는 외부 관리다.
  컬럼 추가·삭제·타입 변경이 필요한 변경은 이 계획의 범위 밖이다.
- **G2. `pending_update` / `pending_file_update` 컬럼의 JSON 표현을 바꾸지 않는다.**
  record의 필드명·필드 순서·중첩 구조를 유지한다. 패키지 이동만 허용한다.
- **G3. `temp_password` 해시 결과 문자열을 바꾸지 않는다.**
  기존 DB에 저장된 해시로 계속 검증에 성공해야 한다(소문자 hex SHA-256).
- **G4. 각 태스크 종료 시 아래가 통과해야 한다.**
  ```
  export JAVA_HOME=$(/usr/libexec/java_home -v 17)
  ./gradlew clean build -x test --console=plain
  ./gradlew test --console=plain
  ```
  기준선(baseline)은 **테스트 61개 전부 통과**다: common 11, core-persistence 1,
  public-api 17, admin-api 1, open-api 31. 태스크가 테스트를 줄이면 안 된다.
- **G5. HTTP 계약을 바꾸지 않는다.** 엔드포인트 경로, 요청/응답 JSON 필드명,
  HTTP 상태코드는 그대로다. 이 계획은 내부 구조 변경만 다룬다.
- **G6. `open-api`, `core-application`, `batch` 모듈은 건드리지 않는다.**
  `common`과 `core-domain`, `core-persistence`는 아래 명시된 범위에서만 수정한다:
  - `common`: Task 2에서 SHA-256 유틸 클래스 1개 추가 (그 외 변경 금지)
  - `core-domain`: Task 3에서 신규 타입 + `build.gradle`, Task 6에서 전이표
  - `core-persistence`: Task 3에서 컨버터 + `build.gradle`, Task 4에서 엔티티, Task 5에서 리포지토리
- **G7. 승인 워크플로우 유스케이스는 `admin-api`에 남긴다.**
  `ApproveCreateArticle`, `ApproveDeleteArticle`, `ApproveUpdateArticle`,
  `List*Articles`, `Get*ArticleDetail`, `ArticleFinder`를 `core-application`이나
  `core-persistence`로 옮기지 않는다.
- **G8. 태스크당 커밋은 한 개 이상, 메시지는 한글 + 타입 프리픽스**(`fix:`, `refactor:`, `chore:`).
- **G9. 리팩터링 태스크(Task 3~6)에서 새 기능을 추가하지 않는다.** 순수 이동/정리다.

## 검증 참고

- Gradle 데몬은 **JDK 17**로 실행해야 한다. Gradle 8.13은 JDK 24+에서 태스크 생성이 깨진다.
- QueryDSL Q-클래스는 `annotationProcessor`가 `build/generated/`에 생성한다.
  엔티티를 옮기면 Q-클래스 생성 위치도 함께 옮겨간다. 한 번 빌드해야 컴파일이 통과한다.
- 린트 도구는 없다. 스타일은 주변 코드에 맞춘다.

---

## Task 1: admin-api `FileEntity.setArticle` no-op 버그 수정

### 대상
`admin-api/src/main/java/re/kr/icuh/drought/adminapi/core/domain/FileEntity.java`

### 문제
```java
public void setArticle(Article article) {
    article = this.article;      // 파라미터에 필드를 대입 — 완전한 no-op
}
```
`Article.addFile(FileEntity)`(같은 패키지 `Article.java:128`)가 이 메서드를 호출한다.
현재 호출부(`Article.java:178`, `Article.java:207`)는 `FileEntity.builder().article(this)`로
연관관계를 미리 세팅하므로 증상이 드러나지 않지만, `addFile()`을 단독으로 쓰면
`article_id`가 채워지지 않는다. 해당 컬럼은 `@JoinColumn(name = "article_id", nullable = false)`다.

### 할 일
1. 대입 방향을 바로잡는다: `this.article = article;`
2. 메서드 이름을 `assignArticle`로 바꾼다. Lombok setter로 오해되지 않게 하고,
   호출부(`Article.addFile`)도 함께 수정한다.
3. 회귀 테스트를 추가한다 — `admin-api/src/test/java/re/kr/icuh/drought/adminapi/core/domain/ArticleTest.java`
   (신규 파일). `Article.addFile(file)` 호출 후 `file.getArticle()`이 그 `Article`을 가리키는지 검증한다.
   빌더에 `.article(...)`을 넘기지 **않은** `FileEntity`로 테스트해야 버그를 실제로 잡는다.
   순수 단위 테스트로 작성한다(`@SpringBootTest` 금지 — 시크릿 없이 돌아가야 한다).

### 완료 조건
- `addFile()` 후 양방향 연관관계가 성립하는 테스트가 통과한다
- G4의 두 명령이 통과한다

---

## Task 2: 프로덕션 해시에서 Testcontainers shaded Guava 제거

### 대상
사본은 **2개가 아니라 3개**다. 사전 스캔에서 확인됐다:
- `public-api/.../publicapi/article/domain/Article.java:107`
- `admin-api/.../adminapi/core/domain/Article.java:112`
- `admin-api/.../adminapi/core/domain/ArticleEditRequest.java:89`  ← **누락되기 쉬움**

그 외:
- `public-api/build.gradle`, `admin-api/build.gradle`
- 신규: `common/src/main/java/re/kr/icuh/drought/common/crypto/Sha256.java`

### 문제
세 곳의 `sha256Encode()`가 아래를 쓴다.
```java
import org.testcontainers.shaded.com.google.common.hash.Hashing;
...
return Hashing.sha256().hashString(tempPassword, StandardCharsets.UTF_8).toString();
```
테스트 라이브러리에 shade된 Guava를 프로덕션 비밀번호 해싱에 쓰고 있다.
Testcontainers 버전을 올리면 shaded 경로가 바뀌어 컴파일이 깨지거나 해시가 달라진다.

### 순서 (이 순서를 반드시 지킬 것)

**G3이 이 태스크의 핵심 제약이다.** 기존 DB의 `temp_password`가 이 해시로 저장돼 있어서
결과가 1바이트라도 달라지면 기존 사용자 전원이 비밀번호 검증에 실패한다.

1. **먼저 특성화 테스트(characterization test)를 작성한다.**
   현재 구현의 출력을 고정한다. 최소 3개 케이스 — ASCII, 한글(UTF-8 멀티바이트), 빈 문자열.
   기대값은 코드에서 계산하지 말고 **하드코딩된 hex 리터럴**로 적는다.
   기대값은 셸에서 뽑아 검증할 수 있다:
   ```
   printf '%s' 'test1234' | shasum -a 256
   ```
   테스트 위치: `public-api/src/test/java/re/kr/icuh/drought/publicapi/article/domain/ArticleTest.java`
   (기존 파일에 추가), `admin-api/.../adminapi/core/domain/ArticleTest.java`(Task 1에서 만든 파일에 추가).
2. 이 테스트가 **현재 구현에서 통과하는지 확인한다.** 통과하지 않으면 기대값이 틀린 것이다.
3. 그 다음에 구현을 교체한다. 사본이 3개이므로 `common`에 유틸 클래스를 하나 만들고
   세 곳이 모두 그것을 호출하게 한다. 새 파일:
   `common/src/main/java/re/kr/icuh/drought/common/crypto/Sha256.java`
   ```java
   package re.kr.icuh.drought.common.crypto;

   public final class Sha256 {
       private Sha256() {}

       public static String hexOf(String raw) {
       try {
           MessageDigest digest = MessageDigest.getInstance("SHA-256");
           byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
           StringBuilder sb = new StringBuilder(hash.length * 2);
           for (byte b : hash) {
               sb.append(Character.forDigit((b >> 4) & 0xF, 16));
               sb.append(Character.forDigit(b & 0xF, 16));
           }
           return sb.toString();
       } catch (NoSuchAlgorithmException e) {
           throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
       }
       }
   }
   ```
   Guava의 `HashCode.toString()`은 **소문자 hex**다. 위 구현도 소문자여야 한다.
   `String.format("%02x", b)`를 써도 되지만 위 방식이 더 빠르다.

   세 클래스의 기존 `public String sha256Encode(String)` 메서드는
   **시그니처를 유지한 채 본문만** `return Sha256.hexOf(tempPassword);`로 바꾼다.
   (`public`을 `private`으로 좁히거나 `static`으로 바꾸지 말 것 — 호출부 영향을 줄인다.)

   `common`은 이미 `public-api`/`admin-api`의 의존이므로 Gradle 변경은 필요 없다.
4. 같은 특성화 테스트가 **그대로 통과하는지** 확인한다. 기대값을 고치면 안 된다 —
   기대값을 고쳐야 한다면 구현이 틀린 것이다.
5. `testcontainers` 의존을 정리한다.
   - `public-api/build.gradle`: `implementation 'org.testcontainers:localstack:1.11.3'` 제거.
     `testImplementation 'org.testcontainers:junit-jupiter:1.11.3'`와
     `testImplementation 'cloud.localstack:localstack-utils:0.2.20'`는 **테스트 스코프이므로 유지**한다.
   - `admin-api/build.gradle`: `implementation 'org.testcontainers:localstack:1.11.3'` 제거.
   - 제거 후 `org.testcontainers`를 참조하는 **main 소스가 없는지** 확인한다:
     `grep -rn 'org.testcontainers' --include='*.java' public-api/src/main admin-api/src/main`
     결과가 비어 있어야 한다.

### 완료 조건
- 특성화 테스트가 교체 전후 **동일한 기대값으로** 통과한다
- `public-api`/`admin-api`의 main 소스에 `org.testcontainers` 참조가 없다
- G4의 두 명령이 통과한다

---

## Task 3: `core-domain` 신설 — enum과 JSON 계약 추출

### 목표
두 모듈에 복붙된 `ArticleStatus`, `FileStatus`, `UpdateArticleRequest`를 `core-domain`
한 곳으로 옮긴다. **순수 이동이며 동작 변화가 없어야 한다.**

`core-domain`은 현재 소스 파일이 0개인 빈 모듈이다. 이 태스크가 처음 채운다.

### 이동 대상과 목적지

| 원본 (2벌) | 목적지 (1벌) |
|---|---|
| `publicapi.article.domain.ArticleStatus`<br>`adminapi.core.domain.ArticleStatus` | `re.kr.icuh.drought.domain.article.ArticleStatus` |
| `publicapi.file.domain.FileStatus`<br>`adminapi.core.domain.FileStatus` | `re.kr.icuh.drought.domain.article.FileStatus` |
| `publicapi.article.dto.request.UpdateArticleRequest`<br>`adminapi.core.api.controller.v2.request.UpdateArticleRequest` | `re.kr.icuh.drought.domain.article.UpdateArticleRequest` |

세 쌍 모두 `package` 줄을 빼면 내용이 동일하다. 먼저 그 사실을 `diff`로 재확인한 뒤 옮긴다.
`UpdateArticleRequest`는 중첩 record `NewFileRequest`를 그대로 포함한다.

`ArticleStatus` 값(순서 유지): `PENDING, APPROVED, REJECTED, DELETED, UPDATED_PENDING, UPDATED_APPROVED, DELETED_PENDING`
`FileStatus` 값(순서 유지): `PENDING, APPROVED, REJECTED, DELETED, UPDATED_PENDING, DELETED_PENDING`

**G2가 걸린다.** `UpdateArticleRequest`의 필드명·순서·중첩 구조를 바꾸면 안 된다:
`title, description, author, authorOrganization, department, tempPassword,
documentTypeCode, subjectDomainCode, source, newFiles`, 중첩
`NewFileRequest(originalFileName, storedFileName, filePath, fileSize, extension)`.
`@Enumerated(EnumType.STRING)`이므로 enum 상수명도 바꾸면 안 된다.

### Gradle 조정

루트 `build.gradle`이 `java-library`가 아닌 `java` 플러그인만 적용하므로 `api` 구성을 쓸 수 없다.
`core-persistence`를 통한 전이 노출이 안 되니 **소비 모듈이 직접 선언**해야 한다.

- `public-api/build.gradle`: `implementation project(':core-domain')` 추가
- `admin-api/build.gradle`: `implementation project(':core-domain')` 추가
- `core-domain/build.gradle`: `implementation 'jakarta.validation:jakarta.validation-api'` 추가
  (`UpdateArticleRequest`의 `@NotNull` 때문)

### 컨버터 통합
`UpdateArticleRequestJsonConverter`도 두 벌 있다:
- `publicapi.article.dto.UpdateArticleRequestJsonConverter`
- `adminapi.core.api.controller.v2.UpdateArticleRequestJsonConverter`

둘을 `re.kr.icuh.drought.persistence.article.converter.UpdateArticleRequestJsonConverter`
한 벌로 합쳐 **`core-persistence`** 에 둔다(엔티티와 함께 있어야 할 영속성 관심사다).
`adminapi.core.api.controller.v2.NewFileRequestJsonConverter`도 같은 패키지로 옮긴다.

`core-persistence/build.gradle`에 `implementation 'com.fasterxml.jackson.core:jackson-databind'`가
필요하다(컨버터가 `ObjectMapper`를 쓴다).

컨버터는 생성자 주입(`ObjectMapper`)을 쓰므로 Spring이 관리하는 `@Converter`다.
양쪽 앱의 `@EntityScan`/`@EnableJpaRepositories`가 이미
`re.kr.icuh.drought.persistence.article`를 포함하므로 추가 스캔 설정은 필요 없다 —
빌드 후 컨텍스트가 뜨는지 확인해 검증한다.

### 완료 조건
- `ArticleStatus`, `FileStatus`, `UpdateArticleRequest`, 두 컨버터의 사본이 각 1벌만 남는다
- `publicapi`/`adminapi`에 위 5개 타입의 정의가 남아 있지 않다 (import만 남는다)
- G4의 두 명령이 통과한다. 특히 **테스트 61개가 그대로 유지**돼야 한다
- 엔드포인트 JSON 계약 변화 없음(G5)

---

## Task 4: `Article` / `FileEntity` 매핑을 `core-persistence`로 통합

이 계획에서 가장 큰 태스크다. Task 3이 끝난 뒤에만 시작할 수 있다.

### 목표
`articles`, `files` 테이블에 대한 JPA 매핑을 한 벌로 만든다.

| 원본 (2벌) | 목적지 (1벌) |
|---|---|
| `publicapi.article.domain.Article`<br>`adminapi.core.domain.Article` | `re.kr.icuh.drought.persistence.article.entity.Article` |
| `publicapi.file.domain.FileEntity`<br>`adminapi.core.domain.FileEntity` | `re.kr.icuh.drought.persistence.article.entity.FileEntity` |

`DocumentType`, `SubjectDomain`이 이미 그 패키지에 있다.

### 컬럼은 합집합

`Article`은 두 매핑의 **합집합**을 갖는다. public 매핑에 없던 두 컬럼이 들어온다:
- `@Column(name = "reject_reason") private String rejectReason;`
- `@Column(name = "pending_file_update") @Convert(converter = NewFileRequestJsonConverter.class) private List<UpdateArticleRequest.NewFileRequest> pendingFileUpdate;`

나머지 컬럼은 두 매핑이 동일하다: `id, title, description, author, author_organization,
department, temp_password, created_at, updated_at, views, status, document_type_id,
subject_domain_id, source, is_deleted, deleted_at, pending_update` + `@OneToMany files`.

G1에 따라 **DDL은 건드리지 않는다.** 두 컬럼은 이미 테이블에 존재한다(admin이 쓰고 있다).

### 도메인 메서드 처리 — 여기가 설계 결정 지점

통합 `Article`이 **가져가는** 것:
- `validatePassword(String)`, `sha256Encode(String)` (Task 2에서 교체된 구현)
- `increaseViews()`
- `delete()` — **public의 살아 있는 시맨틱을 채택**한다:
  `status = DELETED_PENDING; isDeleted = true; deletedAt = now();`
- `reject()` — public 버전. `status != PENDING`이면 `BusinessException(ErrorCode.INVALID_INPUT)`.
  `common`은 `core-persistence`의 기존 의존이므로 사용 가능하다.
- `changeStatus(ArticleStatus)` — admin이 쓴다
- `assignRejectReason(String)` — admin `setRejectReason`을 개명해서 가져온다(setter 오해 방지)
- `updateContent(UpdateArticleRequest)` — public이 쓴다(`pendingUpdate` 스테이징)
- `initPendingUpdate()` — admin `ArticleFinder:95`가 쓴다
- `addFile(FileEntity)` — Task 1에서 고친 버전

통합 `Article`이 **가져가지 않는** 것:
- `softDelete()` (admin) — 호출부 0개인 죽은 코드. **삭제한다.**
- `updateArticle(ArticleEditRequest)` (admin `Article.java:147`) — `ArticleEditRequest`가
  **admin 전용 엔티티**라 `core-persistence`가 참조할 수 없다.
  **호출부인 `adminapi.core.domain.ApproveUpdateArticle`로 로직을 옮긴다.**
- `updateArticleV2(UpdateArticleRequest)` (admin `Article.java:182`) —
  **호출부인 `adminapi.core.domain.ArticleFinder`(94행)로 로직을 옮긴다.**
  (이 메서드는 `updateArticleRequest.newFiles()`로 파일을 재구성하고 `status = APPROVED`로 만든다.
   주석 처리된 `views`/`subjectDomain`/`documentType` 3줄은 **주석인 채로 함께 옮긴다** — G9)

두 `update*` 메서드를 유스케이스로 옮기는 것이 이 계획이 채택한 방향이다:
승인 시 상태를 바꾸는 것은 admin의 워크플로우 관심사이고(G7), 엔티티는 두 앱이 공유한다.

통합 `FileEntity`:
- 필드는 두 매핑이 동일하다: `id, article(@ManyToOne article_id nullable=false),
  original_filename, stored_filename, file_path, file_size, extension, created_at, status`
- 빌더는 `status` 파라미터를 받고, `null`이면 `PENDING`으로 기본값을 준다.
  (근거: public 호출부 3곳은 전부 `.status(PENDING)`을 명시하고,
   admin 호출부 2곳은 빌더 직후 `changeStatus(APPROVED)`를 호출한다. 안전하다.)
- `changeStatus(FileStatus)`, `assignArticle(Article)` (Task 1에서 고친 것)
- `softDelete()` 2개는 **둘 다 죽은 코드다. 삭제한다.**

### 리포지토리·QueryDSL 영향

- `publicapi.article.infra.ArticleRepositoryImpl` — `QArticle` import 경로가
  `re.kr.icuh.drought.persistence.article.entity.QArticle`로 바뀐다
- `adminapi.core.domain.ArticleQueryRepository` — `QArticle`, `QFileEntity` 동일
  (`QArticleEditRequest`는 admin에 그대로 남는다)
- `adminapi.core.domain.FileQueryRepository` — `QFileEntity` 동일
- 리포지토리 인터페이스 자체(`ArticleRepository`, `FileRepository` 등)는 **이 태스크에서 옮기지 않는다.**
  Task 5가 다룬다. 지금은 import만 고쳐 컴파일이 통과하게 한다.

### 스캔 설정
`IcuhPlatformApplication`, `IcuhPlatformAdminApplication`의 `@EntityScan`/`@EnableJpaRepositories`는
이미 `re.kr.icuh.drought.persistence.article`를 포함한다. 자기 모듈 패키지
(`re.kr.icuh.drought.publicapi` / `...adminapi`)는 **리포지토리가 아직 거기 있으므로 유지**한다.

### 완료 조건
- `articles` / `files`에 대한 `@Entity`가 각 1개만 존재한다
  (`grep -rn '@Table(name = "articles")' --include='*.java' */src/main`이 1줄)
- `adminapi`/`publicapi`에 `Article`/`FileEntity` 정의가 남아 있지 않다
- `softDelete()` 정의가 0개다
- G4의 두 명령이 통과한다. 테스트 61개 유지
- G5: 엔드포인트 계약 변화 없음

---

## Task 5: 리포지토리 정리

### 목표
공용 CRUD 계약은 `core-persistence`로, 화면 전용 조회는 각 모듈에 남긴다.

### 현재 배치
- `publicapi.article.infra`: `ArticleRepository`(+`Custom`/`Impl`)
- `publicapi.file.infra`: `FileRepository`
- `adminapi.core.domain`: `ArticleRepository`, `ArticleQueryRepository`,
  `FileRepository`, `FileQueryRepository`, `FileEditRequestRepository`

### 할 일
1. 두 모듈의 `ArticleRepository` / `FileRepository`가 선언한 메서드를 비교한다.
   **완전히 겹치는 기본 CRUD만** `core-persistence`의
   `re.kr.icuh.drought.persistence.article.repository`로 올린다.
2. 한쪽만 쓰는 조회 메서드는 **옮기지 않는다.** 그 모듈에 남긴다.
3. QueryDSL 구현체(`ArticleRepositoryImpl`, `ArticleQueryRepository`, `FileQueryRepository`)는
   **각 모듈에 그대로 둔다.** 화면 전용 쿼리이고 G7에 걸린다.
4. `FileEditRequestRepository`는 admin 전용이다. 건드리지 않는다.
5. 옮긴 뒤 두 앱의 `@EnableJpaRepositories`에서 더 이상 필요 없는 basePackage가 있으면 정리한다.
   **리포지토리가 남아 있는 패키지는 제거하면 안 된다.**

**판단 기준:** 겹치지 않으면 옮기지 않는다. 억지로 통합해서 한쪽만 쓰는 메서드가
공용 인터페이스에 남으면 그게 더 나쁘다. 겹치는 게 `JpaRepository` 기본 메서드뿐이라면
**이 태스크는 "옮길 것 없음"으로 끝내도 된다** — 그 판단과 근거를 리포트에 적는다.

### 완료 조건
- 옮긴 항목과 남긴 항목, 그 근거가 리포트에 정리돼 있다
- G4의 두 명령이 통과한다. 테스트 61개 유지

---

## Task 6: 상태 전이 규칙 단일화

### 목표
`ArticleStatus` 전이 규칙을 `core-domain` 한 곳에 모아, 두 앱 어느 쪽에서도
불법 전이가 일어나지 않게 한다.

### 현재 흩어진 규칙
- `publicapi` `Article.reject()`: `PENDING`이 아니면 `BusinessException(INVALID_INPUT)` — **유일하게 검사하는 지점**
- `adminapi` `ApproveCreateArticle`: 무조건 `APPROVED`로 변경 (검사 없음)
- `adminapi` `ApproveDeleteArticle`: 무조건 `DELETED`로 변경 (검사 없음)
- `adminapi` `ApproveUpdateArticle`: 무조건 `UPDATED_APPROVED`로 변경 (검사 없음)
- `adminapi` `ArticleFinder:43,51`: 무조건 `APPROVED` / `REJECTED`

### 할 일
1. `re.kr.icuh.drought.domain.article.ArticleStatus`에 허용 전이표를 추가한다.
   각 상수가 자신에게서 갈 수 있는 다음 상태 집합을 갖는 형태를 권장한다:
   ```java
   public boolean canTransitionTo(ArticleStatus next) { ... }
   ```
   `EnumSet`을 쓰되, enum 상수 초기화 순환을 피하려면 `static` 초기화 블록이나
   `Supplier`로 지연 초기화한다.
2. 전이표는 **현재 코드가 실제로 수행하는 전이의 합집합**으로 정한다. G9에 따라
   새 규칙을 발명하지 않는다. 최소한 아래는 허용돼야 한다:
   - `PENDING → APPROVED` (ApproveCreateArticle, ArticleFinder:43)
   - `PENDING → REJECTED` (public reject(), ArticleFinder:51)
   - `APPROVED → DELETED_PENDING`, `UPDATED_APPROVED → DELETED_PENDING` (public delete())
   - `DELETED_PENDING → DELETED` (ApproveDeleteArticle)
   - `UPDATED_PENDING → UPDATED_APPROVED` (ApproveUpdateArticle)
   - `UPDATED_APPROVED → APPROVED` (ArticleFinder:94의 updateArticleV2 경로)
   실제 코드를 다시 읽어 **누락된 전이가 없는지 확인**한다. 확신이 없는 전이는
   **허용하는 쪽으로** 정하고 리포트에 적는다 — 잘못 막으면 런타임 장애다.
3. `Article.changeStatus(ArticleStatus)`가 전이를 검증하게 한다.
   위반 시 `BusinessException(ErrorCode.INVALID_INPUT)`.
4. `Article.reject()`의 기존 검사는 전이표로 대체한다(중복 제거).
   단 **예외 타입과 `ErrorCode`는 그대로** 유지한다 — `ArticleTest`가 이를 검증하고 있고 G5에 걸린다.
5. 전이표 단위 테스트를 `core-domain`에 추가한다.
   허용 전이 각각이 `true`, 명백한 불법 전이 몇 개가 `false`인지 검증한다.
   `core-domain/build.gradle`에 `testImplementation 'org.assertj:assertj-core'`가
   이미 루트에서 설정돼 있는지 확인한다(루트 `build.gradle`의 `configure([...])` 블록에 포함돼 있다).

### 위험
이 태스크는 **런타임 동작을 바꾼다.** 전이표가 실제 사용 경로보다 좁으면
승인 버튼이 예외를 던진다. 2번 항목의 "확신 없으면 허용"을 반드시 지킬 것.

### 완료 조건
- 전이 규칙 정의가 `ArticleStatus` 한 곳에만 있다
- 전이표 단위 테스트가 통과한다
- 기존 `ArticleTest`의 `reject()` 테스트 2개가 통과한다.
  (Task 4에서 `Article`이 이동하면서 **import 경로는 이미 바뀌어 있다.**
   이 태스크에서는 단언(assertion) 로직과 기대 예외/`ErrorCode`를 바꾸면 안 된다는 뜻이다.)
- G4의 두 명령이 통과한다
