# `drought` 도메인 설계 — 가뭄영향 리포트 API (open-api)

> **성격**: 신규 도메인 설계 문서 (architectural brainstorming 산출물).
> **인수인계 원본**: `docs/plans/2026-08-30-drought-report-domain-handoff.md`
>   (`drought_impact_report`, Python 저장소에서 넘어온 배경·DB 스키마·프론트 계약 조사)
> **관련 코드**: `open-api/CLAUDE.md`(레이어 패턴), `open-api/spec.md`(S5 DTO 네이밍 등 기존 컨벤션)

---

## 1. 배경 (요약)

`infradna.io.kr`의 "가뭄영향 리포트" 메뉴는 지금 `public-api`의 `article`(문서 등록) 도메인이
채우고 있다. `article`은 가뭄 전용이 아니라 **"인프라재난관리 문서 공유 플랫폼"** — 누구나 문서를
등록하면 승인 절차 거쳐 공개되는 범용 게시판이다(`Article` 엔티티 필드가 "OOO에서 등록한 가뭄
영향 자료입니다" 카드 문구와 정확히 일치함을 확인). 진짜 뉴스 기반 리포트 기능이 없던 시절 그
자리를 채우려고 임시로 연결해둔 것으로 보인다.

`drought_impact_report`(Python) 저장소가 뉴스 기사를 분석해 만든 **월간 가뭄영향 리포트** 데이터를
운영 MySQL(`ACTUAL_DRGHT`, 이 저장소와 동일 DB)에 이미 적재하는 코드까지 완료했다(2026-08-30,
`main` 병합·push 완료). 이 문서는 그 데이터를 읽어 API로 노출하는 **이 저장소의 `drought`
도메인**을 설계한다.

## 2. 범위 (Approach A로 확정)

- **포함**: `open-api`에 `drought` 도메인 신설 — 리포트 목록/상세 엔드포인트, `/v1/summary`의
  `alerts[]`에 drought 항목 최소 기여.
- **제외**: `/v1/summary`를 wildfire 등 기존 4개 도메인까지 채우도록 고치는 것(별도 기술부채,
  이번 범위 아님 — `SummaryService.getSummary()`가 지금 모든 도메인에 대해 `SummaryResponse.empty()`를
  반환하는 스텁임을 확인함). `public-api`의 `article`/프론트 메뉴 분리 자체(프론트·별도 저장소 작업).
  `drought_article`/`drought_article_region_impact` 원본 기사 테이블 직접 조회(Python이 이미
  리포트 테이블로 구워놨으므로 불필요).

## 3. ⚠️ 선행 조건 — DDL 미적용

Python 쪽 `sql/schema.sql`의 4개 신규 테이블(§4)이 **아직 운영 RDS에 실행되지 않았다**. 이 저장소는
`ddl-auto: none`이라 엔티티를 아무리 잘 만들어도 실제 테이블이 없으면 동작하지 않는다 — 구현
착수 전 반드시 실행 여부를 확인할 것.

## 4. 테이블 (Python 쪽 기정 사실, 이 저장소는 읽기 전용)

| 테이블 | PK | 비고 |
|---|---|---|
| `drought_monthly_report` | `report_ym`(VARCHAR7, 'YYYY-MM') | `generated_at`, `article_count`, `detected_sido_count`(0~17) |
| `drought_monthly_report_bucket` | `report_ym`+`sido`+`sigungu`+`impact_code` | `sigungu`는 미분류시 빈 문자열(NULL 아님). `grade`(ENUM 관심/주의/경계/심각), `grade_finalized_at`(NULL=미확정, 한번 찍히면 영구 고정), `representative_link/title`, `keywords`(JSON), `relevance_flag`, `continuity_count` |
| `drought_monthly_report_sido_status` | `report_ym`+`sido` | 매 호 정확히 17행. `detected`, `max_grade`(미감지시 NULL) |
| `drought_report_grade_breaks` | `version`+`impact_code`+`grade` | 참고용, 이번 설계 범위에선 조회 안 함(등급은 이미 버킷에 스냅샷돼 있어 breaks를 직접 볼 일이 없음) — 엔티티도 만들지 않는다(§10) |

**⚠️ 등급명 주의**: 관심/주의/경계/심각 4단계. 기존 통계 집계(`agg_period_grade` 등, 관심/주의/경고/위험)와
**다른 별도 체계**다. 같은 달·같은 지역이라도 두 체계의 등급이 다를 수 있다 — 이 도메인의 API/문서에
어느 체계인지 항상 명시한다.

## 5. 패키지 구조 (wildfire 패턴)

```
core-domain/.../domain/drought/
  ReportGrade.java                         (관심/주의/경계/심각, ordinal 순서 = 심각도. article 도메인의
                                             ArticleStatus와 동일하게 @Enumerated(STRING) 컬럼용 — core-domain은
                                             core-persistence·core-application 양쪽이 의존하므로 여기 둔다)
  DroughtImpactField.java                  (A1~A8 ↔ 한글명, 8행 고정 룩업 — 새 엔티티 안 만듦)
core-persistence/.../persistence/openapi/drought/entity/
  DroughtMonthlyReport.java
  DroughtMonthlyReportBucket.java          (@IdClass — 복합 자연키, 이 저장소 첫 사례)
  DroughtMonthlyReportBucketId.java
  DroughtMonthlyReportSidoStatus.java      (@IdClass)
  DroughtMonthlyReportSidoStatusId.java
core-persistence/.../persistence/openapi/drought/converter/
  KeywordsJsonConverter.java                (List<String> ↔ JSON 컬럼, article 도메인의
                                              UpdateArticleRequestJsonConverter 패턴)
core-persistence/.../persistence/openapi/drought/repository/
  DroughtMonthlyReportRepository.java
  DroughtMonthlyReportBucketRepository.java
  DroughtMonthlyReportSidoStatusRepository.java
core-application/.../application/openapi/drought/
  service/DroughtReportService.java
  response/
    DroughtReportListResponse.java
    DroughtReportDetailResponse.java
    SidoStatusResponse.java
    RegionSectionResponse.java
    ImpactBucketResponse.java
open-api/.../core/api/drought/
  DroughtReportController.java
```

**정정(2026-08-30, 모듈 의존 방향 확인 후)**: `ReportGrade`/`DroughtImpactField`는 애초
`core-application`에 두려 했으나, `core-persistence`는 `core-application`을 의존하지 않는 방향
(`core-persistence → common, core-domain`만; `core-application → common, core-domain,
core-persistence`)이라 엔티티(`core-persistence`)가 이 enum을 쓰려면 더 아래 모듈에 있어야 한다.
기존 `article` 도메인의 `ArticleStatus`(엔티티 상태 enum)가 정확히 이 이유로 `core-domain`에
있는 전례를 그대로 따른다.

**정정(2026-08-30, 실측 후)**: 지역코드(`regionCode`) 룩업 클래스/리소스 파일은 만들지 않기로
했다(§6.3 참조) — `SigunguCodeLookup` + `sigungu-codes.json`은 설계에서 제거.

**⚠️ 실제 DB 값 형식 주의(2026-08-30, Python 쪽 로컬 DB로 실측 확인)**: `sido`/`sigungu` 컬럼은
전체 행정명이 아니라 **축약형**이 저장된다 — 예: `강원도`가 아니라 `강원`, `강릉시`가 아니라 `강릉`,
`해남군`이 아니라 `해남`. 전국 17개 시도 축약형 전체 목록(Python `classify/regions.py`의 `SIDO`
권위 목록과 동일): `서울·부산·대구·인천·광주·대전·울산·세종·경기·강원·충북·충남·전북·전남·경북·경남·제주`.
이 도메인의 `SidoStatusResponse.sido`/`RegionSectionResponse.sido`/`.sigungu`는 DB 값을 그대로
통과시키므로 프론트 표시 문구(예: "강릉시")로 가공하지 않는다 — 그건 프론트 책임으로 둔다(§6.2와
동일한 원칙).

기존 4개 도메인 엔티티는 전부 `@GeneratedValue` 단순 surrogate PK다. Python 쪽 4개 테이블은 전부
**복합 자연키**라 이 저장소에 없던 패턴 — `@IdClass`로 간다(Spring Data JPA 표준 방식).

## 6. API

### 6.1 `GET /api/v1/drought/reports` — 목록 (페이지네이션)

(경로는 기존 도메인 전부가 쓰는 `@RequestMapping("/api/v1/{domain}")` 컨벤션을 따름 —
`agrimarket`/`hydropower`/`freshfood`/`wild-fire-risk`와 동일 패턴. `@RequestMapping("/api/v1/drought")`
+ `@GetMapping("/reports")`.)

```java
record DroughtReportListResponse(
    String reportYm,
    String headlineGrade,        // 이번 호 최고 등급(관심/주의/경계/심각), sido_status에서 계산
    int detectedSidoCount,       // 0~17
    int articleCount,
    List<String> detectedSidoNames
)
```

### 6.2 `GET /api/v1/drought/reports/{reportYm}` — 상세 (없으면 404)

```java
record DroughtReportDetailResponse(
    String reportYm, LocalDateTime generatedAt,
    int articleCount, int detectedSidoCount,
    List<SidoStatusResponse> nationwide,     // 전국 17개 시도, 매번 17개 고정
    List<RegionSectionResponse> regions      // 감지된 지역만
)
record SidoStatusResponse(String sido, boolean detected, String maxGrade)  // 미감지=maxGrade null
record RegionSectionResponse(String sido, String sigungu, List<ImpactBucketResponse> impactFields)
record ImpactBucketResponse(
    String impactCode, String impactName, String grade, boolean gradeFinalized,
    int articleCount, String representativeTitle, String representativeLink,
    List<String> keywords, boolean relevanceFlag, int continuityCount
)
```

`continuityCount`는 정수만 내려주고, "N개월째" 같은 문구 조립은 프론트 책임으로 둔다(백엔드가
문구/다국어까지 책임지지 않도록).

### 6.3 `/v1/summary`(기존 스텁) — drought만 최소 기여 (확정)

**중요한 정정**: `SummaryResponse`/`SummaryAlertResponse`/`SummaryKpiResponse`는 **이미 코드로
존재한다**(`core-application/.../summary/response/`, 전부 빈 값만 반환하는 스텁). 처음 설계 때
참고했던 프론트 목업의 `mentionedRegions[]` 필드는 **실제 `SummaryAlertResponse` record에는
없다** — 그건 프론트 자체 mock-fallback 배열에만 있던 필드다. 진짜 계약은 아래 13개 필드뿐이다:

```java
// core-application/.../summary/response/SummaryAlertResponse.java (이미 존재, 그대로 씀)
record SummaryAlertResponse(
    String id, String category, String dataset,
    String regionCode, String regionName, String title, String description,
    String severity, Number score, Number value, String unit,
    String observedAt, int relatedReportCount
)
```
새 record를 만들지 않고 이 기존 record를 그대로 채워서 반환한다. `mentionedRegions` 관련 설계는
전부 취소.

`SummaryService.getSummary()`가 `SummaryResponse.empty()`를 반환하는 부분에, drought 쪽만 채워
넣는다(다른 도메인은 이번 범위 밖, 그대로 비워둠).

**대상 범위**: **항상 가장 최신 `report_ym`(직전월 리포트) 하나만** 본다 — 몇 달 전 알림이 배너에
남아있으면 "지금 심각한 것"이라는 취지에 안 맞는다.

**alert 단위**: 지역(=`sido`+`sigungu`) 하나가 alert 하나다. 그 지역에 속한 여러 영향분야
버킷 중 **최고 등급**을 그 지역의 대표 등급으로 쓴다(등급 비교는 `ReportGrade` enum ordinal —
관심<주의<경계<심각).

**필터/정렬/상한**: 대표 등급이 **경계 또는 심각**인 지역만 후보. 등급(심각>경계) → 그 다음
지역 전체 기사수(버킷 `article_count` 합) 내림차순 정렬. **상위 3건**만 `alerts[]`에 담는다
(N=3은 하드코딩 상수로 시작, 나중에 조정 가능하게 설정값으로 뺄 수도 있음 — 지금은 YAGNI로 상수).

**필드 채우기 규칙**:

| 필드 | 값 |
|---|---|
| `id` | `"drought-" + reportYm + "-" + sido + "-" + sigungu` (합성 문자열, 안정적) |
| `category` / `dataset` | `"drought-report"` 고정 |
| `regionCode` | **항상 `null`** — 아래 "regionCode 정정" 참조 |
| `regionName` | `sigungu`(없으면 `sido`), DB 값 그대로(축약형, 위 §5 표기 주의 참조) |
| `title` | 그 지역의 대표 버킷(최고 등급, 동률이면 기사수 최다) 1건의 `representativeTitle` |
| `description` | `"{영향분야명} 부문 관련 기사 {건수}건 발행"` 템플릿(대표 버킷 기준) |
| `severity` | 등급→severity 매핑(아래) |
| `score` | 등급 고정 매핑: 관심=25, 주의=50, 경계=75, 심각=95 (연속값 없음, 근사치임을 문서화) |
| `value` / `unit` | `value`=그 지역 전체 기사수 합, `unit`="article_count" (목업의 `rainfall_ratio`는 우리에게 없는 지표라 억지로 안 채움) |
| `observedAt` | `drought_monthly_report.generated_at`의 날짜부(LocalDate), 문자열로 |
| `relatedReportCount` | 그 지역에 감지된 영향분야(버킷) 개수 |

등급→프론트 severity 매핑:
```
관심 → "info"    주의 → "warning"    경계 → "warning"    심각 → "danger"
```

**regionCode 정정(2026-08-30, 브레인스토밍 중 재검토)**: `regionCode`는 `regionName`과 별도
필드로 존재하므로 원래는 안정적인 공식 코드가 들어가야 맞다. 그런데 (1) 실제 DB의 `sido`/`sigungu`는
축약형이라(§5) 시군구명 → 법정동코드 매핑표를 만들어도 매칭 기준 자체가 비표준이고, (2) 검증된
코드 3건(강릉=42150, 합천=48890, 고흥=46770)조차 애초에 프론트 JS 번들에서 추출한 값이라 —
프론트가 이미 자체 지역명↔코드 매핑을 갖고 있을 가능성이 높다. 이 필드를 이번 최소 기여 범위에서
실제로 소비하는 곳이 확인된 바도 없다. 따라서 `SigunguCodeLookup` 클래스/리소스 파일은 만들지
않고, **drought 쪽 `regionCode`는 항상 `null`**로 내려준다. 실제로 코드가 필요해지면(예: 프론트가
지도 매칭에 이 필드를 쓴다는 게 확인되면) 그때 행정안전부 법정동코드 공공데이터 기준으로 추가한다.

**⚠️ 등급 escalation 시점 주의(2026-08-30, 실제 로컬 DB로 리포트 생성해 확인)**: 새 버킷은 항상
`관심`으로 시작하고, 실제 등급(주의/경계/심각) 확정은 생성 후 **1개월 실시간 유예**가 지나야
일어난다(`report_grades.finalize_due_grades`). 즉 이 기능을 막 배포한 시점에는 아직 confirm된
경계/심각 버킷이 하나도 없어 `alerts[]`가 한동안 빈 배열일 수 있다 — 이는 버그가 아니라 설계상
당연한 특성이다. 컨트롤러/서비스 테스트에는 이 경우(후보 지역 0건 → `alerts` 빈 리스트)도 반드시
포함한다.

## 7. 조회 로직

- **목록**: `drought_monthly_report`를 `report_ym` 내림차순 페이징. `headlineGrade`/`detectedSidoNames`는
  해당 `report_ym`의 `sido_status` 중 `detected=true`인 행에서 계산 — MySQL ENUM 집계에 기대지
  않고, `관심<주의<경계<심각` 순서의 Java enum(`ReportGrade`, ordinal 비교)으로 애플리케이션에서
  명시적으로 최댓값을 구한다.
- **상세**: `drought_monthly_report` 단건 조회(없으면 404) + `sido_status` 17행 그대로 `nationwide`로
  + `bucket` 전체를 `(sido, sigungu)`로 그룹핑해 `RegionSection`으로 조립. `impact_code`(A1~A8) →
  한글명은 `DroughtImpactField` enum으로 매핑(Python의 `DroughtImpactField`와 동일 코드 체계,
  8행 고정이라 별도 엔티티/리포지토리 없이 하드코딩).

## 8. 에러 처리

**정정**: `BusinessException(ErrorCode.XXX)`는 `public-api` 모듈의 관례다. 이 도메인이 속한
`open-api`/`core-application`/`core-persistence`는 `common` 모듈의 **`CoreException(ErrorType)`**을
쓴다(`re.kr.icuh.drought.common.openapi.error`). 없는 `reportYm` 조회 시 새 에러 타입을 만들지 않고
기존 `ErrorType.DATA_NOT_FOUND`를 그대로 던진다 — 이 모듈은 도메인별 전용 에러 타입 없이
`INVALID_PARAMETER`/`DATA_NOT_FOUND`/`ENDPOINT_NOT_FOUND`/`DEFAULT_ERROR` 4종만 범용으로 쓰는
관례라 이걸 따른다.

## 9. 테스트

`open-api/spec.md` S11이 이미 권장해둔 순서를 새 도메인부터 실천한다: 서비스 단위 테스트(그룹핑·
등급 최댓값 계산 로직) → 컨트롤러 슬라이스 테스트(`@WebMvcTest`, 200/404 응답) → 리포지토리
`@DataJpaTest`. 기존 도메인엔 이 3단계가 다 갖춰져 있지 않지만, 새 도메인은 처음부터 갖춘다.

## 10. 미해결 / 구현 단계에서 확정할 것

- `drought_report_grade_breaks` 엔티티/조회 API는 이번 범위에서 만들지 않는다(YAGNI — 지금 설계의
  어떤 엔드포인트도 안 씀). 나중에 "등급 산정 근거 보기" 같은 기능이 실제로 필요해지면 그때 추가.
- `regionCode`는 항상 `null`(§6.3 "regionCode 정정" 참조) — 지역코드 룩업 자체를 만들지 않기로
  확정. 프론트가 이 필드를 실제로 쓰는 게 확인되면(예: 지도 매칭) 그때 행정안전부 법정동코드
  공공데이터 기준으로 별도 매핑을 추가한다. 임의로 지어내지 않는다.
- DDL 적용 시점·주체(§3) — 로컬 dev DB에는 2026-08-30에 적용 및 실측 완료(기사 748건, 버킷 212개,
  16/17 시도 감지, 2026-05 기준). **운영 RDS에는 아직 미적용** — 구현 착수 전 반드시 확인/적용.
- 배포 초기 `alerts[]` 공백 특성(§6.3 "등급 escalation 시점 주의") — 버그 아님, 문서화만 해둔다.
- `public-api`의 `article` 문서 20건을 어떻게 별도 메뉴로 분리할지는 프론트/다른 저장소 작업이라
  이 스펙 밖 — 여기서는 이 백엔드가 "가뭄영향 리포트"라는 이름에 맞는 진짜 데이터를 내놓는 것까지만
  책임진다.
