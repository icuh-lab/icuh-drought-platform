# 가뭄영향 리포트 — Spring `drought` 도메인 설계를 위한 인수인계

> 이 문서는 `drought_impact_report`(Python, 데이터 생산자) 저장소에서 진행한 별도 세션의 산출물이다.
> 그 세션은 브레인스토밍 → 설계 문서 → 구현 계획 → subagent-driven 구현(10개 태스크 + 최종 리뷰 1회
> 수정)까지 마쳤고, Python 쪽 코드는 이미 `main`에 병합·push됐다. 이 문서는 **이 저장소(Spring)에서
> 새로 시작할 세션**이 바로 브레인스토밍에 들어갈 수 있도록 필요한 사실만 정리한다 — 설계 결정은
> 이 문서가 대신하지 않는다.

---

## 1. 배경

`infradna.io.kr`(Next.js 프론트) 홈 카드뉴스와 `?view=reports`에 있는 "가뭄영향 리포트"는 현재
기관들이 등록한 문서(백서·보고서) 20건을 보여주고 있고, 카드 문구("뉴스 기반 자동 생성", 등급
"낮음", "분석 기사 0건")는 실제와 무관한 placeholder다. `drought_impact_report` 저장소가 **뉴스
기사를 분석해 만든 진짜 월간 리포트**로 이 자리를 대체하기로 확정했다(사용자 명시적 결정). Python
쪽은 그 데이터를 운영 MySQL(`ACTUAL_DRGHT` 스키마, 이 저장소와 동일 DB)에 적재하는 부분을
완료했다. **이 저장소가 해야 할 일은 그 DB를 읽어 프론트가 기대하는 API로 노출하는 `drought`
도메인을 새로 만드는 것**이다.

## 2. 범위

- 신규 도메인: `drought` (기존 `agrimarket`/`freshfood`/`hydropower`/`wildfire`와 나란히).
- 이 저장소 전체에 `drought_article`이나 리포트 관련 코드가 **단 한 줄도 없다**(2026-08-30 확인,
  grep 0건). 완전히 빈 도메인부터 시작해야 한다.
- 프론트엔드(`~/Desktop/workspace_front/icuh-drought-platform-fo`, Next.js)는 이미 실제 API
  연동을 전제로 만들어져 있어(§4 참고) **변경이 필요 없을 가능성이 높다** — 백엔드 API가 준비되면
  그대로 붙을 것으로 보이지만, 이 세션에서 프론트 코드를 직접 열어 확인은 해야 한다.

## 3. ⚠️ 먼저 확인/실행해야 할 것 — DDL 미적용

Python 쪽 DDL(§5)은 **로컬 `sql/schema.sql`에만 있고, 아직 운영 RDS(`ACTUAL_DRGHT`)에 실행되지
않았다**. 이 저장소는 `ddl-auto: none`(스키마는 이미 존재한다고 가정, Hibernate가 테이블을 만들지
않음) 관례를 쓰므로, `drought` 도메인 코드를 개발/테스트하기 전에 **누군가 그 DDL을 운영 DB에
직접 실행해야 한다**. 이건 Python 쪽 세션도, 이 세션도 자동으로 하지 않는 작업이다 — 사용자에게
먼저 확인할 것.

## 4. 프론트엔드가 실제로 기대하는 것 (Next.js 코드 직접 분석, 2026-08-30)

`icuh-drought-platform-fo`는 Next.js(App Router), Caddy 뒤에서 자체 호스팅. `open-api.infradna.io.kr`
를 호출한다(자체 백엔드, `public`/`open` 두 베이스가 코드에 정의돼 있음: `https://api.infradna.io.kr`,
`https://open-api.infradna.io.kr`).

### 4.1 `/v1/summary` — 실제로 살아있는 엔드포인트, 지금은 비어있음

호출 결과(2026-08-30 직접 확인):
```json
{"result":"SUCCESS","data":{"generatedAt":"2026-08-30T07:51:48...+09:00","alerts":[],"kpis":[]},"error":null}
```
`alerts`/`kpis`가 항상 빈 배열이라 프론트가 목업으로 대체 중이다. 프론트는 4가지 상태 문구를
이미 갖고 있다: "리포트 API 갱신"(success) / "리포트 API 확인 중 · 목업 표시"(loading) /
"리포트 API 데이터 없음 · 목업 표시"(empty, 지금 이 상태) / "리포트 API 오류 · 목업 표시"(error).
즉 **`alerts` 배열에 최소 1개라도 들어오면 그 즉시 목업이 사라지고 실제 데이터로 전환**되도록
이미 구현돼 있다 — 프론트 수정이 필요 없을 수 있다는 근거.

### 4.2 목업 alert 항목의 실제 필드 shape (프론트 JS 번들에서 그대로 추출)

이게 `open-api`가 채워야 할 `alerts[]` 원소의 계약으로 보인다(목업이 이 shape을 그대로 쓰고
있으므로):
```js
{
  id: "mock-drought-goheung",
  category: "drought-report",
  dataset: "drought-report",
  regionCode: "46770",
  regionName: "고흥",
  title: "고흥 가뭄영향 '매우높음' 단계 진입",
  description: "관수 차질 리포트 3건 발행 · 최근 3개월 강수량 평년 대비 48%",
  severity: "danger",           // 문자열 등급 — Python 쪽 관심/주의/경계/심각과 매핑 필요(§6-1)
  score: 90,
  value: 48,
  unit: "rainfall_ratio",
  observedAt: "2026-08-05",
  relatedReportCount: 3,
  mentionedRegions: [
    {
      sidoName: "전남", sigunguName: "고흥군", sigunguCode: "46770",
      regionCode: "46770", regionName: "고흥",
      impactCode: "agriculture", impactName: "농업",
      note: "고흥군 농업 부문 관수 차질 언급"
    }
  ]
}
```
카드 클릭 시 `category === "drought-report"`면 리포트 뷰로 이동하는 라우팅도 이미 구현돼 있다.

### 4.3 리포트 목록/상세 화면 — 진짜 동작하지만 콘텐츠가 자료 등록물

`?view=reports`는 실제로 동작하는 기능(목업 아님)이고, 지금은 기관이 등록한 문서 20건을 보여준다.
카드 구조(실측): 등급 배지 + 제목 + "OOO에서 등록한 가뭄 영향 자료입니다" + 날짜 + `#전국` 태그 +
푸터 "날짜 · 분석 기사 N건 · 뉴스 기반 자동 생성". 필터 사이드바는 최초 참고했던 프로토타입의
하드코딩(고흥·전남/합천·경남/강릉·강원 지역 체크박스, 기간 라디오)이 그대로 남아있어 지금 데이터와
안 맞는다 — 이 부분은 실데이터 연동 시 정리 필요.

### 4.4 API 카탈로그 등록 패턴 (API 센터 문서 자동 생성용)

기존 도메인들은 `open-api`의 API 센터(`?view=api`)에 이런 shape으로 카탈로그 등록돼 있다(JS
번들에서 그대로 추출):
```js
{ group: "농산물", method: "GET", path: "/api/v1/agrimarket/market-price",
  name: "월간 시장 가격 및 반입량 예측 정보", description: "...", params: [...] }
```
`drought` 도메인 엔드포인트도 이 패턴을 따라 카탈로그에 등록하는 게 일관성 있어 보인다. 에러
응답 포맷은 전역 공통: `{"status":404,"message":"...","error":{"code":"ENDPOINT_NOT_FOUND","details":"..."}}`.

## 5. Python 쪽이 만들어 넘기는 데이터 (완료됨)

### 5.1 DB 스키마 — 신규 4개 테이블 (DDL은 §3 참고, `sql/schema.sql` 원본은
`~/Desktop/workspace_python/drought_impact_report/sql/schema.sql` 참고)

| 테이블 | 역할 | 핵심 컬럼 |
|---|---|---|
| `drought_monthly_report` | 월간호 1건 (PK=`report_ym` 'YYYY-MM') | `generated_at`, `article_count`, `detected_sido_count`(0~17) |
| `drought_monthly_report_bucket` | 지역×분야 세부 항목 (PK=report_ym+sido+sigungu+impact_code) | `article_count`, `grade`, `grade_finalized_at`(NULL=미확정), `representative_link/title`, `keywords`(JSON), `relevance_flag`(대표기사 관련도 낮음 표시), `continuity_count`(연속 등장 개월수) |
| `drought_monthly_report_sido_status` | 전국 17개 시도 완전성 (매 호 정확히 17행) | `detected`, `max_grade`(감지 안 됐으면 NULL) |
| `drought_report_grade_breaks` | 리포트 전용 Jenks 등급구간, 버전 관리 | `version`, `impact_code`, `grade`, `lower_bound` |

기존(변경 없음) 테이블: `drought_article`(기사 원본), `drought_article_region_impact`(기사×지역×영향
bridge), `impact_field`(A1~A8 영향분야 코드표). 이 저장소가 이미 알고 있는 테이블들이다
(`open-api/outputs/db_check_work/실측가뭄_사례검증_DB현황조사_20260805.md` 참고 — 마이그레이션
직전 조사본).

### 5.2 ⚠️ 등급명이 기존과 다르다 — 절대 혼동 금지

리포트 등급은 **관심/주의/경계/심각** 4단계다. 기존 통계 집계 테이블(`agg_period_grade` 등, 이
저장소가 이미 알고 있을 수 있는 것)은 **관심/주의/경고/위험**을 쓴다. 서로 다른 체계이고, 리포트
쪽 4개 신규 테이블만 새 이름을 쓴다. API 응답 필드/문서에 어느 체계인지 명시할 것. (§4.2의 목업
`severity: "danger"` 같은 프론트 쪽 영문 라벨과의 매핑도 이 세션에서 정해야 함 — Python 쪽은
한글 4단계만 만들었다.)

### 5.3 발행/등급 정책 (API 설계 시 알아야 할 것)

- 월간호는 **월 1회** 생성. 새로 생성된 버킷은 등급 계산 없이 무조건 `grade="관심"`,
  `grade_finalized_at=NULL`로 시작한다.
- 발행 **1개월 후** 딱 한 번 실제 등급을 계산해 스냅샷 고정한다(`grade_finalized_at`이 채워짐).
  그 이후로는 절대 바뀌지 않는다 — API가 이 값을 캐싱해도 안전하다는 뜻이지만, 반대로 "잠정치"
  같은 상태 표시는 Python 쪽엔 없다(설계 논의 끝에 "관심 그대로 보여주고 구분 안 함"으로 확정).
- breaks(등급 기준선)는 리포트 전용으로 **연 1회만** 재보정한다. 기존 통계 대시보드 쪽 breaks와
  독립적이다.
- 전국 17개 시도는 매 호 전부 나열된다 — 감지 안 된 시도는 `detected=false, max_grade=null`로
  명시적으로 존재하지, 행 자체가 없는 게 아니다. API가 "이상 없음" 같은 문구로 렌더링해도 되고,
  프론트가 이미 그런 처리를 하고 있을 수도 있다(§4.3의 목업 alert 부재 상태 UI 참고).

### 5.4 알려진 한계 (API 설계 시 고려할 것 — 최종 리뷰에서 확인된 것들)

- `finalize` 로직은 **이미 존재하는 버킷의 재계산**만 한다 — 발행 시점엔 없던 지역/분야 조합이
  나중에 새 기사로 생기면, 그 report_ym을 다시 `generate`하기 전까진 버킷 자체가 안 생긴다(의도적
  범위 제한, 설계 문서 §8 참고).
- 대표기사 선정은 "본문 최장 + 최신일자" 휴리스틱이라 가끔 주제와 무관한 기사가 뽑힐 수 있다
  (`relevance_flag`가 그걸 표시하려는 용도) — API 응답에 이 플래그를 그대로 노출해 프론트가
  "관련도 검토 필요" 같은 처리를 할 수 있게 하는 게 좋다.
- `--year-month` 입력 형식 검증이 Python CLI 쪽에 없다. API 파라미터 검증은 이 저장소가 독립적으로
  갖춰야 한다.

## 6. 이 세션에서 결정해야 할 것 (설계 필요, 미리 정하지 않음)

1. **모듈 배치**: `drought` 도메인을 `open-api`에만 둘지, 문서 등록(기관 자료 20건) 대체/이관은
   `admin-api`가 맡을지. 기존 기관 등록물을 어떻게 이전/보존할지도 아직 미정(Python 쪽 설계
   문서 §8에 남겨둔 미해결 항목).
2. **엔드포인트/응답 shape**: §4.2의 목업 shape을 그대로 계약으로 삼을지, 조정할지.
   `severity`(영문) ↔ 관심/주의/경계/심각(한글) 매핑 방식.
3. **캐싱 전략**: `grade_finalized_at`이 찍힌 버킷은 다시 안 바뀌므로 캐싱 가능 — `open-api/spec.md`
   S15(보류 중인 캐싱 항목)와 묶어서 갈지 별도로 갈지.
4. **엔티티/리포지토리 설계**: `core-persistence`에 4개 신규 엔티티 추가 시 `wildfire` 도메인
   패턴(entity+repo in `core-persistence` → service+DTO in `core-application` → controller in
   `open-api`)을 그대로 따를지 확인.
5. **DDL 적용 시점**(§3) — 언제, 누가 실행할지.

## 7. 참고 파일

- Python 쪽 설계 문서(전체 맥락): `~/Desktop/workspace_python/drought_impact_report/docs/DESIGN_MONTHLY_DROUGHT_REPORT.md`
- Python 쪽 구현 계획(실제 코드 레퍼런스): `~/Desktop/workspace_python/drought_impact_report/docs/superpowers/plans/2026-08-29-monthly-drought-report.md`
- Python 쪽 신규 코드: `~/Desktop/workspace_python/drought_impact_report/src/drought_impact_report/report/`,
  `store/report_writer.py`, `store/report_grades.py`, `report_cli.py`
- 이 저장소의 기존 DB 조사: `open-api/outputs/db_check_work/실측가뭄_사례검증_DB현황조사_20260805.md`
- 이 저장소의 기존 코드 컨벤션: `open-api/CLAUDE.md` (레이어 패턴, `ApiResponse` 래퍼, 에러 처리 관례)
- 화면 설계 목업(Artifact, 등급명은 구버전 관심/주의/경고/위험으로 그려져 있어 반영 전):
  https://claude.ai/code/artifact/e0459134-fd71-4a04-bbdb-272ca500cb29
