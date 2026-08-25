# SDD ledger — plan: docs/plans/entity-consolidation-plan.md

Branch: refactor/entity-consolidation
Baseline commit: 14f7633 (main) — `build -x test` 통과, 테스트 61개 green
  (common 11 / core-persistence 1 / public-api 17 / admin-api 1 / open-api 31)
Isolation: 워크트리 대신 feature 브랜치를 in-place 사용.
  Ruling: 사용자가 이 디렉터리를 IntelliJ로 열어 작업 중이고, 신규 저장소라 병행 작업이
  없다. 워크트리로 옮기면 사용자의 IDE 세션과 분리된다 — "main에서 구현하지 않는다"는
  격리 의도는 feature 브랜치로 충족된다.
  비용(틀렸을 때): 작업 중 워킹트리가 더러워져 baseline 비교가 번거로워질 수 있음.
  `git stash`/`git checkout main`으로 복구 가능.

## 사전 충돌 스캔 (Task 1 디스패치 전)

### 파일/인터페이스를 공유하는 태스크 쌍

| 쌍 | 공유 대상 | 생산 → 소비 | 결과 |
|---|---|---|---|
| T1 → T2 | `adminapi/core/domain/ArticleTest.java` | T1이 파일 생성 → T2가 특성화 테스트 추가 | OK (T2가 T1 이후임을 명시) |
| T1 → T2 | `adminapi/core/domain/Article.java` | T1이 addFile 호출부 수정 → T2가 sha256Encode 본문 수정 | OK (다른 메서드) |
| T1 → T4 | `FileEntity.assignArticle` | T1이 개명·수정 → T4가 통합 엔티티로 이관 | OK (T4가 "Task 1에서 고친 것" 명시) |
| T2 → T4 | `Article.sha256Encode` | T2가 본문 교체 → T4가 통합 엔티티로 이관 | OK (T4가 "Task 2에서 교체된 구현" 명시) |
| T2 → T3 | `public-api/build.gradle`, `admin-api/build.gradle` | T2가 testcontainers 제거 → T3이 core-domain 의존 추가 | OK (다른 줄, 순차) |
| T3 → T4 | `persistence.article.converter.*` | T3이 컨버터 이관 → T4의 Article이 `@Convert`로 참조 | OK |
| T3 → T6 | `core-domain ArticleStatus` | T3이 생성 → T6이 전이표 추가 | OK |
| T4 → T5 | 리포지토리 위치 | T4가 import만 수정하고 이관은 보류 → T5가 이관 | OK (T4에 명시적 보류 문구 있음) |
| T4 → T6 | `Article.changeStatus` / `reject()` | T4가 통합 엔티티에 배치 → T6이 전이 검증 주입 | OK |
| T5 → T6 | 없음 (겹치는 파일 없음) | — | OK |

### 태스크 자체 정합성

| 태스크 | 확인 항목 | 결과 |
|---|---|---|
| T1 | 명시한 테스트 파일이 신규인지 | OK — admin-api에 `core/domain/ArticleTest.java` 없음 |
| T2 | 명시한 대상 파일 개수가 실제와 맞는지 | **❌ 결함 A** (아래 판정) |
| T2 | G6이 지정 작업을 허용하는지 | **❌ 결함 B** (아래 판정) |
| T3 | 이동 대상 3쌍이 실제로 동일한지 | OK — `diff`로 package 줄만 차이 확인 완료 |
| T3 | G2(JSON 계약)를 지킬 수 있는 이동인지 | OK — 컨버터가 `@JsonTypeInfo` 미사용, 필드명 기반 |
| T4 | 옮길 수 없는 메서드를 식별했는지 | OK — `updateArticle(ArticleEditRequest)`가 admin 전용 타입 참조, 이관처 명시됨 |
| T4 | 삭제 대상이 실제로 죽은 코드인지 | OK — `softDelete()` 3개 모두 호출부 0개 확인 완료 |
| T4 | 빌더 기본값 변경이 안전한지 | OK — 호출부 5곳 전수 확인, 전부 status 명시 또는 직후 changeStatus |
| T5 | 빠져나갈 구멍이 있는지 | OK — "옮길 것 없음으로 끝내도 된다" 명시 |
| T6 | 기존 테스트 불변 주장이 T4와 모순되지 않는지 | **❌ 결함 C** (아래 판정) |

### 판정 (실행 전)

Ruling A: T2의 대상은 2개가 아니라 3개다 — `adminapi.core.domain.ArticleEditRequest:89`에도
  `sha256Encode` 사본이 있다(생성자 81행에서 호출되는 살아 있는 코드). 계획의 Task 2 대상 목록을
  3개로 수정했다. — 근거: 하나를 남기면 Testcontainers 의존을 `build.gradle`에서 제거하는 순간
  admin-api 컴파일이 깨진다. — 비용(틀렸을 때): 없음. 누락 시 컴파일 실패로 즉시 드러난다.

Ruling B: G6을 완화해 `common`에 SHA-256 유틸 클래스 1개 추가를 허용한다. 사본이 3개이므로
  각각에 MessageDigest 구현을 복붙하면 중복을 3벌로 고착시킨다. `common`은 PHASES.md Phase 2에서
  "shared utility types"의 자리로 정의된 모듈이다. — 비용(틀렸을 때): `common`의 표면이 클래스
  하나만큼 넓어진다. 되돌리기 쉽다.

Ruling C: T6의 "기존 ArticleTest가 수정 없이 통과" 요구는 T4와 모순된다 — T4가 `Article`을
  `core-persistence`로 옮기면 테스트의 import 경로가 반드시 바뀐다. "단언 로직과 기대 예외/
  ErrorCode를 바꾸지 않는다"로 문구를 수정했다. — 비용(틀렸을 때): 없음. 문구 정정이다.

## 진행

Task 1: dispatched (sonnet) base=cd6bd69 → DONE, commit ed180c7
  "fix: admin-api FileEntity.setArticle no-op 버그 수정"
  구현자 보고: G4 게이트 통과, admin-api 테스트 1→2, 전체 61→62.
  버그 재현 확인(수정 전 실패 → 수정 후 통과)했다고 보고함.
  → 리뷰 패키지 review-cd6bd69..ed180c7.diff, 태스크 리뷰어 디스패치(sonnet)
  참고: 이후 태스크의 테스트 기준선은 **62개**다 (G4 문구의 61은 Task 1 이전 값).
Task 1: 리뷰 결과 — Spec ✅ / Quality Approved / 발견 0건.
  ⚠️ "게이트 미재실행" 항목은 컨트롤러가 직접 검증해 해소:
  clean build -x test BUILD SUCCESSFUL, test BUILD SUCCESSFUL,
  62개 전부 통과 (common 11 / core-persistence 1 / public-api 17 / admin-api 2 / open-api 31).
Task 1: complete (commits cd6bd69..ed180c7, review clean)

Task 2: dispatched (sonnet) base=079c3c2
  Ruling (Task 3 사전): admin `UpdateArticleRequestJsonConverter<T>`의 타입 파라미터 `<T>`는
  어디에도 쓰이지 않는 잔재다. `<T>` 없는 public 버전을 통합본으로 채택한다.
  — 근거: admin의 `@Convert(converter = ...class)`가 이미 raw 타입 사용이라 영향 없음.
  — 비용(틀렸을 때): 없음. 컴파일 실패로 즉시 드러난다.
Task 2: DONE, commit a811b0f
  "fix: 프로덕션 비밀번호 해싱에서 Testcontainers shaded Guava 제거"
  구현자 보고: RED(교체 전 통과) → GREEN(교체 후 동일 리터럴로 통과), 전체 68개(62+6), 0 실패.
  컨트롤러 독립 검증:
   - 기대 hex 리터럴 3개가 public/admin ArticleTest 양쪽에 실제로 박혀 있음 (총 6개 단언)
   - `grep org.testcontainers` on src/main → 없음
   - build.gradle: `implementation ...localstack` 제거됨, public-api의 testImplementation 2개만 잔존
   - **G3 실패모드 커버리지 분석**: 세 테스트 벡터가 hex 인코딩의 두 고전 버그를
     실제로 덮는다 — 선행 0 필요 바이트(0d/0c/08/04)와 Java 음수 byte(>0x7f, 벡터당 13~19개).
     즉 부호확장 버그나 선행 0 누락 버그가 있으면 이 테스트가 반드시 실패한다.
  → 리뷰 패키지 review-079c3c2..a811b0f.diff, 태스크 리뷰어 디스패치(opus — G3 위험도 반영)
Task 2: 리뷰 결과 — Spec ✅ / Quality Approved / Critical·Important 0건.
  리뷰어가 hex 인코딩 루프를 JDK17에서 독립 컴파일해 **256개 바이트값 전수 비교**,
  `String.format("%02x", b)`와 문자 단위로 동일함을 확인. Guava 패리티 성립.
Task 2: minor (deferred): ArticleEditRequest.sha256Encode에 자체 테스트 없음
  (Sha256.hexOf가 6개 테스트로 고정돼 있어 수용. 향후 그 메서드 단독 수정 시 무방비)
Task 2: minor (deferred): public-api의 cloud.localstack:localstack-utils,
  org.testcontainers:junit-jupiter가 이제 죽은 의존 — 참조하는 테스트 소스 없음.
  브리프가 유지를 지시했으므로 위반 아님. 별도 의존성 정리 태스크 후보.
Task 2: minor (deferred): public-api Article.java:12 import 그룹핑 흐트러짐 (미관)
Task 2: complete (commits 079c3c2..a811b0f, review clean, 3 minor deferred)
  ⚠️ 저장소 밖 미검증 항목: 운영 DB의 temp_password가 전부 이 코드 경로로 쓰였는지
  (더 오래된 해시 스킴이 섞여 있을 가능성)는 저장소 안에서 확인 불가. 사용자에게 보고할 것.

Task 3: dispatched (sonnet) base=5028fd8
Task 3: DONE, commit 45f4243
  "refactor: core-domain 신설 — ArticleStatus/FileStatus/UpdateArticleRequest 및 JSON 컨버터 통합"
  68/68 통과. 실제 분포: common 11 / core-persistence 1 / public-api 20 / admin-api 5 / open-api 31.
  Correction: Task 3 브리프에 적은 분포(public-api 23 / admin-api 2)는 컨트롤러의 계산 착오였다.
    Task 2가 public/admin 테스트에 3개씩 추가했으므로 17+3=20, 2+3=5가 맞다. 총계 68은 일치.
    구현자가 이를 발견해 보고함 — 회귀 아님.
  컨트롤러 독립 검증:
   - ArticleStatus/FileStatus/UpdateArticleRequest/컨버터 2종 모두 정의가 **1벌씩만** 존재
   - G2 검증: baseline(14f7633) 대비 enum 상수명 순서 동일, UpdateArticleRequest의
     필드명·순서·중첩구조가 `diff` 기준 완전 동일 → 기존 pending_update JSON 역직렬화 안전
  → 리뷰 패키지 review-5028fd8..45f4243.diff, 태스크 리뷰어 디스패치(sonnet)
  Ruling (Task 4 사전 보강): Task 4에 `pending_update` JSON 왕복 테스트를 요구사항으로 추가했다.
  — 근거: G2는 이 계획에서 두 번째로 위험한 제약인데(첫째는 G3), 지금까지의 검증은 "필드명이
    같다"는 정적 비교뿐이고 컨버터가 런타임에 배선되는지를 증명하는 테스트가 하나도 없다.
    엔티티가 core-persistence로 오는 Task 4가 기존 H2 @DataJpaTest 슬라이스를 쓸 수 있는
    유일한 시점이다.
  — 비용(틀렸을 때): @DataJpaTest가 Jackson을 자동구성하지 않아 ObjectMapper 빈 추가가
    필요하고, 그래도 안 되면 Task 4가 지연된다. 그 경우 테스트를 빼고 진행해도 계획은 성립한다.
Task 3: 리뷰 결과 — Spec ✅ / Quality Approved / Critical·Important 0건.
  리뷰어 추가 확인: 컨버터의 런타임 배선이 이미 증명돼 있다 — 두 앱의 contextLoads()가
  @SpringBootTest + application-test.yml(H2, ddl-auto: create-drop)로 돌고, Hibernate가
  EntityManagerFactory 부트스트랩 시 생성자 주입 @Convert 컨버터를 Spring 빈 컨테이너에서
  해석해야만 통과한다. 두 테스트 XML 모두 failures=0 errors=0.
  (단 **직렬화 왕복 자체**는 여전히 미증명 → Task 4의 왕복 테스트는 유효하므로 유지한다.)
Task 3: minor (deferred): NewFileRequestJsonConverter에 @Converter 애노테이션 없음(기존 상태).
  @Convert(converter=...)로 명시 지정해 쓰므로 동작에는 문제 없음.
Task 3: complete (commits 5028fd8..45f4243, review clean, 1 minor deferred)

Task 4: dispatched (opus — 계획 내 최대 태스크) base=46d4235
Task 4: DONE_WITH_CONCERNS, commit c653871
  "refactor: articles/files JPA 매핑을 core-persistence로 통합" (38 files, +300/-337)
  69/69 통과 (68 + 왕복 테스트 1). common 11 / core-persistence 2 / public-api 20 / admin-api 5 / open-api 31.
  컨트롤러 독립 검증: articles/files 엔티티 각 1개 ✅ / softDelete 잔존 0 ✅ /
    G7 승인 유스케이스 4개 모두 admin-api 잔류 ✅ / updateArticleV2의 주석 3줄 보존 ✅

  Ruling (구현자 우려 1 — 엔티티 변경 표면): **수용.** 브리프의 공백이 맞다. 통합 Article의
    메서드 목록을 닫아 두면서 동시에 updateArticle/updateArticleV2 본문을 admin 유스케이스로
    옮기라고 했는데, 옮긴 쪽에서 쓸 방법이 없었다. 구현자가 좁은 의도표현 뮤테이터 3개
    (applyApprovedContent / applyApprovedClassification / clearFiles)를 추가하고 상태 전이는
    changeStatus로 유스케이스에 남긴 것은 setter 노출이나 admin 전용 타입 참조보다 낫다.
    — 비용(틀렸을 때): 메서드 3개 + 호출부 2곳 되돌리면 됨. 리뷰어가 동작 동등성을 확인해야 한다.

  Ruling (구현자 우려 2 — H2 컬럼 폭): **수용.** 프로덕션 매핑에 @Column(length=...)를 넣는 대신
    테스트 스키마만 @Sql ALTER TABLE로 넓힌 것은 G1(스키마 불변)에 맞다.
    — 다만 이 우회가 **운영 쪽 질문을 드러낸다**: `articles.pending_update`의 실제 MySQL 타입이
      varchar(255)라면 지금도 실데이터가 잘리고 있다는 뜻이다. 저장소 안에서 확인 불가.
      **사용자에게 배포 전 확인 항목으로 보고할 것.**
    — 비용(틀렸을 때): 없음. 테스트 전용 변경이다.

  Escalated (구현자가 minor로 분류했으나 컨트롤러가 격상): public-api의 INSERT가 이제
    reject_reason / pending_file_update를 명시적으로 포함한다(이전에는 매핑에 없어 생략 → DB 기본값).
    두 컬럼이 NOT NULL이고 기본값이 없다면 **신규 게시글 등록이 깨진다.** UPDATE 경로는
    관리 엔티티를 다시 쓰는 것이라 무해하다. 리뷰어에게 명시적으로 판단을 요청하고,
    사용자에게 배포 전 확인 항목으로 보고한다.
  → 리뷰 패키지 review-46d4235..c653871.diff, 태스크 리뷰어 디스패치(opus)
Task 4: 리뷰 결과 — Spec ✅ / Quality Approved / Critical·Important 0건.
  리뷰어가 이관 메서드 2개를 문장 단위로 원본(git show 46d4235:...)과 대조:
   - updateArticle → ApproveUpdateArticle.applyEditRequest: 11개 대입 전부 보존, 인자 순서
     전치 없음, clearFiles→루프→changeStatus(APPROVED)→addFile 순서 동일, 종료 상태
     UPDATED_APPROVED 동일. 재배치된 것은 서로 독립인 대입들뿐.
   - updateArticleV2 → ArticleFinder.applyPendingUpdate: 종료 상태 APPROVED 동일,
     applyApprovedClassification을 호출하지 않음 = views/subjectDomain/documentType이
     주석 처리된 원래 동작을 정확히 보존.
   - FileEntity 빌더 기본값(null→PENDING): 호출부 6곳 전수 안전 확인.
   - 왕복 테스트: flush()+clear() 실재, usingRecursiveComparison + 5개 component×2건
     containsExactly로 중첩 newFiles까지 단언. @Bean ObjectMapper가 실제로 필수임도 확인.

  Ruling (격상했던 INSERT nullability 건): **park + 사용자 보고.** 리뷰어 판정으로 조건이
    명확해졌다 — `reject_reason` 또는 `pending_file_update`가 NOT NULL일 때만 깨진다.
    저장소 안에 DDL/Flyway/Liquibase가 없어(ddl-auto: none) 확인 불가.
    수정하지 않기로 한다: 두 컬럼이 nullable이면(반려 사유 컬럼의 성격상 그럴 가능성이 높다)
    아무 변경도 필요 없고, 추측으로 @DynamicInsert를 넣으면 **다른 nullable 컬럼들의 INSERT
    동작까지 바뀌는** 별도의 블라스트 반경이 생긴다. @Column(insertable=false)도 마찬가지로
    앞으로 그 컬럼을 INSERT할 길을 영구히 막는다.
    → 사용자가 `SHOW CREATE TABLE articles;` 한 번으로 확인하는 것이 가장 싸고 정확하다.
    — 비용(틀렸을 때): 두 컬럼이 NOT NULL이면 배포 후 신규 게시글 등록이 제약 위반으로 실패한다.
      최소 수정은 Article에 @DynamicInsert. 배포 전 확인 항목으로 사용자에게 명시 보고한다.

Task 4: minor (deferred): public-api가 이제 pending_file_update를 읽는다 — 잘못된 레거시
  데이터가 있으면 이전엔 건드리지 않던 공개 엔드포인트가 500을 낼 수 있다(작성자는 없음).
Task 4: minor (deferred): import 배치가 java.util 뒤에 오는 파일 4곳 (미관).
Task 4: minor (deferred): ArticleFinder:117-119의 보존된 주석이 `this.views = ...`로 남아
  있어 새 위치(ArticleFinder 빈)에서 오해를 부른다. 한 줄 리드인 주석 필요.
Task 4: minor (deferred): NewFileRequestJsonConverter는 여전히 테스트 없음. 이제 두 앱에서 동작.
Task 4: minor (deferred): public-api ArticleTest가 main 대응이 없는 패키지에 남음.
Task 4: complete (commits 46d4235..c653871, review clean, 1 parked + 5 minor deferred)

Task 5: dispatched (sonnet) base=c653871

  Ruling (Task 6 재작성): 계획 초안의 전이표가 두 군데 틀렸고, 태스크의 성격 자체가 잘못
    기술돼 있었다. Task 4 완료 시점 코드를 직접 읽어 10개 전이 경로와 각각의 소스 상태를
    표로 도출하고 Task 6을 다시 썼다.
    발견 1: 초안의 "UPDATED_APPROVED → APPROVED"는 틀렸다. 실제는 **APPROVED → APPROVED
      자기 전이**다(findPendingUpdateArticle이 status='APPROVED' AND pendingUpdate IS NOT NULL).
      전이표에서 자기 전이를 빠뜨리면 수정 승인이 런타임에 깨진다.
    발견 2 (더 중요): 상태를 바꾸는 10개 경로 중 소스 상태가 실제로 좁혀지는 것은 3개뿐이다.
      나머지 7개는 status 필터 없이 findById/findArticle로 로드한다. 목록 조회만 상태로
      필터링할 뿐 **변경 경로에는 사실상 제약이 없다.**
      → 따라서 Task 6은 "흩어진 규칙 모으기"가 아니라 "없던 규칙 새로 만들기"다.
        전이표를 좁게 만들면 지금 200을 반환하던 관리자 조작이 예외를 던진다 = G5 위반
        (같은 요청에 새 실패가 생기는 것도 계약 변경이다).
      → 소스가 좁혀지지 않는 경로는 모든 현재 상태를 소스로 허용하도록 규칙을 명시했다.
        표가 막는 것은 어떤 경로로도 도달 불가능한 전이뿐이다(예: DELETED → PENDING).
    — 비용(틀렸을 때): 전이표가 여전히 너무 좁으면 특정 관리자 조작이 배포 후 실패한다.
      되돌리기는 changeStatus에서 검증 호출 한 줄을 빼면 된다.

Task 5: DONE_WITH_CONCERNS, commit 5b10fa8 "refactor: FileRepository를 core-persistence로 통합"
  69/69 통과, 기준선과 동일. 두 앱의 contextLoads() 모두 실행·통과.
  구현자 우려는 무해: 워킹트리에 나타난 정체불명 계획 편집 = 컨트롤러가 쓴 Task 6 재작성본.
    구현자가 명시적 경로 스테이징으로 커밋에서 제외한 것은 올바른 처리다. 별도 커밋(46d4235 이후)으로 반영함.
  컨트롤러 독립 검증:
   - FileRepository는 core-persistence 1곳에만 존재 ✅
   - ArticleRepository는 public/admin 양쪽 유지 = 분리 판단대로 ✅
   - ArticleRepositoryImpl / ArticleQueryRepository / FileQueryRepository /
     FileEditRequestRepository 모두 원위치 유지 ✅ (G7)
   - 두 앱의 @EntityScan/@EnableJpaRepositories가 자기 패키지 + persistence.article 둘 다 유지 ✅
  → 리뷰 패키지 review-c653871..5b10fa8.diff, 태스크 리뷰어 디스패치(sonnet)
Task 5: 리뷰 결과 — Spec ✅ / Quality Approved / 발견 0건.
  리뷰어가 판단 자체도 검증: FileRepository 통합은 손실 없음(진짜 byte-identical, 노출 메서드 0),
  ArticleRepository 분리 유지가 옳다(합치면 ArticleRepositoryCustom이나 admin 전용 @Query 2개를
  공용 인터페이스로 밀어넣게 되어 브리프가 경고한 안티패턴). 다른 중복 리포지토리도 없음.
Task 5: complete (commits c653871..5b10fa8, review clean)

Task 6: dispatched (opus — 런타임 동작 변경 + 판단 비중) base=edba259

Task 6: DONE_WITH_CONCERNS, commit a2b6176
  "refactor: ArticleStatus 전이 규칙을 core-domain 한 곳으로 단일화"
  159/159 통과 (69 → +90). core-domain 0→80, core-persistence 2→12.
  컨트롤러 과잉구현 점검: 신규 테스트 파일 2개, 테스트 **메서드**는 14개
    (@ParameterizedTest 12 + @Test 6 중 신규분). 90개 케이스는 7x7 전이 행렬을
    파라미터화한 결과로 적정. ArticleStatus 구현은 128줄. 과잉 아님.
  결과 표: 모든 상태 → {APPROVED, REJECTED, DELETED, DELETED_PENDING, UPDATED_APPROVED} 허용,
    모든 상태 → {PENDING, UPDATED_PENDING} 금지. APPROVED→APPROVED 자기 전이 명시 포함.
  구현자가 테스트가 무는지 검증: 순환 초기화 주입 → ExceptionInInitializerError,
    row 2 축소 → 14개 실패. 둘 다 되돌리고 게이트 재실행 clean.

  구현자 우려 1 (브리프 8·10행의 근거가 틀림): **구현자가 옳다.** 컨트롤러가 표를 만들 때
    상태 필터가 걸린 조회 쿼리를 근거로 댔으나, 그 쿼리들은 목록/상세 엔드포인트용이고
    실제 변경 경로는 findById(8행 ArticleFinder.mergeArticle) / findUpdatedRequestArticle(id)(10행)로
    상태를 검사하지 않는다. 즉 **코드가 브리프보다 넓다.**
    결과 표에는 영향 없음 — 6·9행이 이미 해당 목표 상태를 모든 소스에서 허용하기 때문이다.
    구현자가 코드를 고치지 않고(그게 G5/G9 위반) 표에만 반영한 판단이 옳다.
    → 이 발견은 "확신 없으면 허용" 규칙이 실제로 사고를 막았음을 보여준다.
  구현자 우려 3 (프롬프트의 "7개 무제약 경로"는 6개): 맞다. 1행은 생성이라 전이가 아니다.
    결과 동일.
  → 리뷰 패키지 review-edba259..a2b6176.diff, 태스크 리뷰어 디스패치(opus)
Task 6: 리뷰 결과 — Spec ✅ / Quality Approved / Critical·Important 0건.
  리뷰어가 저장소 전체에서 changeStatus( / this.status = / .status( / setStatus /
  new Article( / @Modifying 를 독립 grep해 **프로덕션 changeStatus 호출부가 정확히 6곳이며
  전부 컴파일타임 리터럴**임을 확인. 10개 경로 전부 허용됨을 표로 검증.
  G5 전수 확인: 프로덕션의 모든 목표 상태가 {APPROVED,REJECTED,DELETED,DELETED_PENDING,
  UPDATED_APPROVED}에 속하고 이 5개는 7개 소스 전부에서 허용된다 → 기존에 성공하던
  changeStatus 호출은 하나도 실패로 바뀌지 않는다. 반복 조작(2번 승인/반려 등)도 자기 전이로 허용.
  금지 집합(→PENDING, →UPDATED_PENDING)은 호출 그래프상 실제 도달 불가 확인.
  순환 초기화: static {} 블록은 상수 배정 이후 실행 + 맵 사전 시딩으로 NPE 불가.
Task 6: minor (deferred): ArticleStatus.java:32의 8행 근거 텍스트가 브리프의 틀린 설명을
  그대로 갖고 있다(12줄 아래에서 정정되긴 함). 행 텍스트 정렬 필요.
Task 6: minor (deferred): Article.java:154의 `this.status != null` 예외 처리에 테스트가 없다.
Task 6: minor (deferred): 경로4/경로6 테스트가 byte-identical, 초기화 테스트가 6개 경로를 포섭.
  1:1 추적성 목적이면 방어 가능하나 독립 커버리지로 오해하면 안 됨.
Task 6: minor (deferred): 순환초기화 검증 테스트는 고유 탐지력이 없다(그 실패는 어차피 80개
  테스트를 전부 깨뜨림). 구현자의 변이 실험이 주장만큼 증명하지는 못함. 설계 자체는 정확.
Task 6: 정보성: "전이 규칙이 한 곳에" 기준은 브리프가 명시 허용한 2개 예외를 빼고 성립한다 —
  Article.reject()의 PENDING 가드, ArticleEditRequest.changeStatus의 미가드(10행은 문서화되나 미강제).
Task 6: complete (commits edba259..a2b6176, review clean, 4 minor deferred)

=== 전체 태스크 완료. 최종 전체 브랜치 리뷰 단계 ===

최종 전체 브랜치 리뷰 (opus, 14f7633..a2b6176, 62 files +1386/-482):
  판정: **머지 가능, 배포 전 확인 1건**. 드리프트 없음 — 두 번 이동한 것 없고, Task 3의 결정을
  4/6이 되돌린 것 없고, 이관 메서드가 baseline 원본과 문장 단위로 일치.
  통합 Article의 13개 뮤테이터 전부 살아있는 호출부 보유, 그룹핑도 응집적. 모듈 그래프 비순환.
  Important 4건 (전부 정확성이 아니라 **비중** 문제):
   1. 테스트 질량이 위험과 반대로 실림 — core-domain이 159개 중 80개(50%)인데 그 표는
      결국 `next != PENDING && next != UPDATED_PENDING`으로 환원된다. 반면 이번 브랜치가
      실제로 **옮긴** 행위 밀도 높은 두 메서드(ArticleFinder.applyPendingUpdate,
      ApproveUpdateArticle.applyEditRequest)는 테스트가 0개. admin-api 전체가 5개.
   2. Sha256이 자기 모듈(common)에서 미테스트. G3(최고위험 제약)이 public/admin의
      중복 특성화 테스트 6개에만 의존. `:common:test`는 해시에 대해 아무것도 증명 못함.
   3. 전이표 우회가 4개(원장엔 2개로 기록했음): delete(), reject(), 빌더 기본값,
      그리고 ArticleEditRequest.changeStatus는 표를 아예 호출 안 함(10행은 문서화되나 미강제).
   4. UpdateArticleRequest가 계약 3개(HTTP @RequestBody / pending_update JSON / admin 읽기모델)를
      지면서 문서가 0. **Task 3 이동 과정에서 G2 경고가 유실됐다** — 유일한 기존 경고인
      public-api/ONBOARDING.md:81,83이 이제 삭제된 파일을 가리킨다.
  Minor: core-domain의 common 의존이 미사용(유일한 부정직 선언) / public-api의 querydsl-apt가
    생성할 것 없음 / ONBOARDING.md 구문 / ArticleStatus:92,94 동일 호출 / 빈 디렉터리 잔존.
  이월 목록 트리아지: **13건 전부 defer, must-fix 0건.** 대신 목록에 없던 2건을 승격 권고:
    Sha256 테스트 부재, UpdateArticleRequest G2 경고 부재.

  Ruling (parked INSERT nullability 재판정): 리뷰어가 저장소만으로 **2개 컬럼 → 1개로 좁혔다.**
    - pending_file_update는 사실상 배제: 아무도 쓰지 않고, admin 매핑엔 원래 있었고,
      @DynamicUpdate가 없으므로 기존 admin 승인/반려가 이미 매 UPDATE마다 그 컬럼을 NULL로
      다시 쓴다. NOT NULL이었다면 **지금 이미 운영에서 실패하고 있어야 한다.**
    - 남는 유일한 실패 모드: `reject_reason`이 NOT NULL이면서 DEFAULT가 있는 형태.
    - 또한 baseline의 public INSERT가 이미 pending_update와 deleted_at에 명시적 NULL을 썼으므로
      그 둘은 NULL 허용이 증명된다 → 컨트롤러가 "@DynamicInsert는 다른 컬럼 동작도 바꾼다"고
      한 판단은 맞지만 범위를 과대평가했다.
    - 결론: 투기적 수정을 하지 않은 판단은 유지. 단 사용자에게 줄 확인 항목을 더 날카롭게 —
      `reject_reason`만 보면 되고, NOT NULL이면 최소 수정은 @DynamicInsert가 아니라
      Article.java:88에 @Column(insertable = false)다.

수정 웨이브 1회 디스패치 (skill: ONE fix dispatch, then ONE scoped re-review)
