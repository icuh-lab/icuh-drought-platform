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
   기대값은 아래를 **그대로** 쓴다(`printf '%s' '<입력>' | shasum -a 256`으로 검증됨):

   | 입력 | 기대 hex (소문자) |
   |---|---|
   | `test1234` | `937e8d5fbb48bd4949536cd65b8d35c426b80d2f830c5c308e2cdec422ae2244` |
   | `비밀번호1234` | `a0191747a9c89b6b08303dc7f1497b4d34ebd44e978045eb0d5da4c04f04705f` |
   | `` (빈 문자열) | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |

   한글 케이스가 핵심이다 — UTF-8 멀티바이트 인코딩이 어긋나면 여기서만 깨진다.
   `Article`의 `sha256Encode`는 `public` 인스턴스 메서드이므로 테스트에서
   `Article` 인스턴스를 만들어 직접 호출하면 된다.
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

둘의 **실질 내용은 동일하다.** 유일한 차이는 admin 쪽 클래스 선언에 붙은
**사용되지 않는 타입 파라미터 `<T>`** 다:
```java
// public
public class UpdateArticleRequestJsonConverter implements AttributeConverter<UpdateArticleRequest, String>
// admin  ← <T>가 어디에도 쓰이지 않는다
public class UpdateArticleRequestJsonConverter<T> implements AttributeConverter<UpdateArticleRequest, String>
```
**`<T>` 없는 public 버전을 채택한다.** admin의 `@Convert(converter = UpdateArticleRequestJsonConverter.class)`는
현재 raw 타입으로 쓰이고 있어 `<T>`를 없애도 영향이 없다.

통합본을 `re.kr.icuh.drought.persistence.article.converter.UpdateArticleRequestJsonConverter`
한 벌로 만들어 **`core-persistence`** 에 둔다(엔티티와 함께 있어야 할 영속성 관심사다).
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

### G2를 실제로 증명하는 테스트 (이 태스크에서 추가)

현재 `pending_update` JSON 컨버터가 런타임에 제대로 배선되는지를 **증명하는 테스트가 없다.**
컨버터는 `ObjectMapper`를 생성자 주입받는 Spring 관리 `@Converter`이고, 두 앱에서는
`spring-boot-starter-web`이 `ObjectMapper`를 자동 구성해 주지만 그 사실을 검증하는 테스트가 없다.

엔티티가 `core-persistence`로 오는 이 태스크가 그 자리를 만든다.
기존 `core-persistence/src/test/java/re/kr/icuh/drought/persistence/PersistenceSliceTest.java`
(H2 기반 `@DataJpaTest`, `@SpringBootConfiguration static class TestApplication` 포함)에
왕복 테스트를 추가한다:

1. `UpdateArticleRequest`를 채운 `Article`을 저장한다
2. `flush()` + `clear()`로 영속성 컨텍스트를 비운다 (1차 캐시가 아니라 DB에서 읽어야 한다)
3. 다시 읽어 `pendingUpdate`의 모든 필드가 왕복하는지 단언한다. 중첩 `newFiles`도 포함한다

**주의:** `@DataJpaTest`는 Jackson을 자동 구성하지 않는다. `ObjectMapper` 빈이 없으면
컨버터를 생성하지 못해 컨텍스트 로딩부터 실패한다. `TestApplication`에 `@Bean ObjectMapper`를
추가해 해결한다 — 이건 테스트 설정이지 프로덕션 변경이 아니다.

이 테스트가 통과하면 G2가 "필드명이 같다"는 정적 확인을 넘어 **실제 직렬화·역직렬화 왕복**으로
증명된다. 컨텍스트 로딩이 실패하면 그것 자체가 발견해야 할 결함이다 — 우회하지 말고 보고할 것.

### 완료 조건
- `articles` / `files`에 대한 `@Entity`가 각 1개만 존재한다
- `pending_update` 왕복 테스트가 통과한다 (위 항목)
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

### 사전 분석 (컨트롤러가 미리 수행함 — 그대로 신뢰하지 말고 재확인할 것)

| 인터페이스 | public | admin | 겹침 |
|---|---|---|---|
| `ArticleRepository` | `JpaRepository<Article,Long>` + `ArticleRepositoryCustom`<br>(`findApprovedArticles(ArticleRequest, Pageable)`) | `JpaRepository<Article,Long>` +<br>`findArticlesByStatusOrderByCreatedAtDesc(ArticleStatus)`<br>`findPendingUpdateArticle()` | **`JpaRepository` 기본 메서드뿐** |
| `FileRepository` | `JpaRepository<FileEntity,Long>`<br>**추가 메서드 없음** | `JpaRepository<FileEntity,Long>`<br>**추가 메서드 없음** | **완전 동일** |

따라서 예상 결론은:
- `FileRepository` → `core-persistence`로 **통합 대상**. Task 4가 `FileEntity`를 이미 합쳤으므로
  두 인터페이스가 문자 그대로 같아진다.
- `ArticleRepository` → **통합하지 않는다.** 각자 자기 모듈 전용 쿼리를 갖고 있고,
  공통분모가 `JpaRepository` 기본 메서드뿐이라 올려봐야 얻는 게 없다.

이 분석이 맞는지 직접 확인하고, 다르면 리포트에 근거와 함께 적는다.

### 할 일
1. 위 분석을 재확인한다. **완전히 겹치는 것만** `core-persistence`의
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
`ArticleStatus` 전이 규칙을 `core-domain` 한 곳에 모아, 두 앱에 흩어진 상태 지식을 단일 출처로 만든다.

### 컨트롤러가 Task 4 완료 시점 코드에서 직접 도출한 사실

**상태를 쓰는 지점과 그 소스 상태:**

| # | 쓰는 지점 | 목표 상태 | 소스 상태 | 소스가 좁혀지는 근거 |
|---|---|---|---|---|
| 1 | `publicapi` `ArticleService:95` (생성) | `PENDING` | — (신규) | 빌더 기본값 |
| 2 | `Article.delete()` | `DELETED_PENDING` | **제약 없음** | `findById`, 상태 필터 없음 |
| 3 | `Article.reject()` | `REJECTED` | `PENDING` **만** | 메서드 안에 명시적 가드 |
| 4 | `ApproveCreateArticle:19` | `APPROVED` | **제약 없음** | `findArticle(id)`, 필터 없음 |
| 5 | `ApproveDeleteArticle:19` | `DELETED` | **제약 없음** | `findArticle(id)`, 필터 없음 |
| 6 | `ArticleFinder.updateArticleStatus:47` | `APPROVED` | **제약 없음** | `findById`, 필터 없음 |
| 7 | `ArticleFinder.rejectArticle:55` | `REJECTED` | **제약 없음** | `findById`, 필터 없음 |
| 8 | `ArticleFinder.applyPendingUpdate:116` | `APPROVED` | `APPROVED` | `findPendingUpdateArticle()`가 `status='APPROVED' AND pendingUpdate IS NOT NULL` |
| 9 | `ApproveUpdateArticle:41` (Article) | `UPDATED_APPROVED` | **제약 없음** | `findArticle(...)`, 필터 없음 |
| 10 | `ApproveUpdateArticle:24` (ArticleEditRequest) | `UPDATED_APPROVED` | `UPDATED_PENDING` | 조회 쿼리가 `articleEditRequest.status.eq(UPDATED_PENDING)` |

**이 표가 뒤집는 것:** 이 계획의 초안은 "`UPDATED_APPROVED → APPROVED`"를 8번 경로로 적었으나,
실제로는 **`APPROVED → APPROVED` 자기 전이**다. 초안을 신뢰하지 말고 위 표를 쓸 것.

**핵심 발견:** 상태를 바꾸는 10개 경로 중 소스 상태가 실제로 좁혀지는 것은 3·8·10번뿐이다.
나머지는 전부 ID로 로드하며 상태를 검사하지 않는다. 목록 조회 쿼리만 상태로 필터링할 뿐,
**변경 경로에는 사실상 아무 제약이 없다.**

### 이 태스크의 성격 — 반드시 읽을 것

따라서 이 태스크는 "흩어진 규칙을 모으는 일"이 아니라 **"지금까지 없던 규칙을 새로 만드는 일"** 이다.
전이표를 좁게 만들면 지금 200을 반환하던 관리자 조작이 예외를 던진다. 그것은 G5(HTTP 계약 불변)
위반이다 — 같은 요청에 새로운 실패가 생기는 것도 계약 변경이다.

**따라서 이 태스크의 규칙:**

1. 전이표는 **위 표에서 소스가 좁혀지지 않는 경로(2·4·5·6·7·9번)에 대해 현재 모든 상태를 소스로 허용**한다.
   실제로 도달 가능한 조합을 임의로 배제하지 않는다.
2. 좁혀지는 3개(3·8·10번)만 그 제약을 반영한다.
3. `Article.reject()`의 기존 가드는 **그대로 둔다.** 이미 테스트 2개가 검증하고 있고,
   예외 타입과 `ErrorCode`가 계약이다.
4. 결과적으로 표가 막는 것은 **어떤 경로로도 도달 불가능한 전이뿐**이다
   (예: `DELETED → PENDING`). 이게 이 태스크가 정직하게 얻을 수 있는 전부다.

### 할 일

1. `re.kr.icuh.drought.domain.article.ArticleStatus`에 전이 판정을 추가한다:
   ```java
   public boolean canTransitionTo(ArticleStatus next)
   ```
   `EnumSet`을 쓰되 enum 상수 초기화 순환을 피할 것(`static` 초기화 블록 또는 지연 초기화).
   같은 상태로의 자기 전이(8번 `APPROVED → APPROVED`)를 반드시 허용한다.
2. `Article.changeStatus(ArticleStatus)`가 이 판정을 쓰게 한다.
   위반 시 `BusinessException(ErrorCode.INVALID_INPUT)`.
3. **`core-domain`에 전이표 단위 테스트를 추가한다.** 위 표의 10개 경로가 전부 허용되는지
   각각 단언하고, 도달 불가능한 전이 몇 개가 거부되는지 단언한다.
   `core-domain`은 루트 `build.gradle`의 `configure([...])` 블록에 포함돼 있어
   `assertj-core`가 이미 testImplementation으로 붙어 있다. 확인만 하면 된다.
4. 표를 `ArticleStatus`의 Javadoc으로 남긴다 — 이 지식이 코드에서 사라지지 않도록.

### 하지 말 것

- 변경 경로에 상태 필터를 추가하지 말 것(예: `findArticle`을 상태로 좁히기). 그건 별도 태스크다.
- `reject()`의 가드를 전이표로 대체하지 말 것. 중복처럼 보이지만 예외 계약이 걸려 있다.
- 도달 가능성이 의심스러운 전이를 막지 말 것. **확신이 없으면 허용한다.**

### 완료 조건
- 전이 규칙 정의가 `ArticleStatus` 한 곳에만 있다
- 위 표의 10개 경로가 전부 허용됨을 검증하는 단위 테스트가 통과한다
- 기존 `ArticleTest`의 `reject()` 테스트 2개가 통과한다
  (Task 4에서 `Article`이 이동했으므로 import 경로는 이미 바뀌어 있다.
   단언 로직과 기대 예외/`ErrorCode`를 바꾸면 안 된다는 뜻이다.)
- G4의 두 명령이 통과한다
