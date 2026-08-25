# ICUH Platform API — 개선 포인트 명세 (spec.md)

> 현재 코드베이스(Spring Boot 3.5.6 / Java 17 / JPA / MySQL) 분석을 기반으로 도출한 개선 포인트입니다.
> **단순(저위험·국소) → 복잡(구조적·전사적)** 순으로 정렬했습니다.
> 각 항목의 실행 순서/묶음은 [plan.md](./plan.md)를 참고하세요.

---

## 분류 기준

| 난이도 | 의미 |
|--------|------|
| 🟢 Trivial | 1~2 파일 국소 수정, 동작 변화 거의 없음 |
| 🟡 Easy | 한 도메인/레이어 내 정리, 명확한 패턴 적용 |
| 🟠 Medium | 여러 도메인에 걸친 일관성 작업, 설계 결정 동반 |
| 🔴 Hard | 구조/인프라 변경, 빌드·배포·운영에 영향 |

---

## 🟢 Trivial — 즉시 고칠 수 있는 것

### S1. 오타 수정 — `HydroPowerRepository`
- `HydroPowerRepository.java:21` 파라미터명 `danName` → `damName`.
- 동작에는 영향 없으나 가독성/일관성 저해.

### S2. 불필요한 공백/포맷 정리
- `HydroPowerService.java` 끝부분의 빈 줄 2개 등 자투리 포맷 정리.
- 도메인 간 import 정렬·미사용 import 점검.

### S3. `@Transactional(readOnly = true)` 일관성
- `AgriMarketService`에만 `@Transactional(readOnly = true)`가 붙어 있고
  `FreshFoodService`, `HydroPowerService`에는 없음.
- 모든 조회 서비스 메서드(현재 전부 read-only)에 일관 적용.

---

## 🟡 Easy — 한 도메인/레이어 내 정리

### S4. 요청 DTO 검증 일관화
- `FreshFoodIndexRequest`는 `@NotBlank` + `@Pattern`으로 연/월 검증이 충실함.
- 나머지 요청 DTO(`AgriMarketRequestDto`, `HydroPowerRequestDto`, `WildFireRiskIndexRequest`)도
  동일 수준의 `year/month/day/location/damName` 검증 규칙을 갖추도록 통일.
- 검증 정규식·메시지를 도메인 간 재사용 가능한 형태로 정리.

### S5. DTO/네이밍 컨벤션 통일  ✅ 완료 (2026-06-16)
> 모든 DTO 타입명을 `~Request`/`~Response`로 통일(`~Dto` 폐지). JSON 필드명(응답 키)은
> 계약 유지를 위해 보존하고 타입명만 변경. 20개 클래스 리네이밍 후 빌드·테스트 통과.
- 접미사 혼용: `agrimarket`/`hydropower`는 `~RequestDto`, `~ResponseDto`, `~Dto`.
  `freshfood`/`wildfire`는 `~Request`, `~Response`.
- 프로젝트 전역에서 한 가지 컨벤션(예: `~Request` / `~Response`)으로 통일.

### S6. `ErrorType` 세분화  ⏸️ 보류 (2026-06-16 결정)
> 실제 throw 지점 없이 enum만 늘리면 죽은 코드. 새 throw 지점이 생기는 단계에서 함께 도입.
- 현재 `INVALID_PARAMETER`, `DATA_NOT_FOUND`, `DEFAULT_ERROR` 3종뿐.
- 도메인 특화 에러(예: 잘못된 연/월 범위, 지원하지 않는 지역/댐명 등)나
  공통 케이스(예: `METHOD_NOT_ALLOWED`)를 추가해 응답 코드 명확화.

### S19. 등급 임계값 비교 타입 정리 (테스트 중 발견)
- `FreshVegetableIndex.createSummary` 등이 `Float index >= 115.1`처럼 **`Float`를 `double`
  리터럴과 비교** → 경계값에서 부동소수 확장 오차로 분류가 어긋날 수 있음(예: `85.1f`).
- 임계값을 `float` 리터럴(`115.1f`)로 맞추거나, 비교 전 타입/스케일을 통일.
- 참고: insights.md I11.

### S7. 매직 값 상수화
- 응답의 indicator color, 등급 임계값 등 분류 로직에 쓰이는 리터럴을
  도메인 VO 내부 상수/enum으로 추출(이미 VO 패턴이 있으므로 그 안으로 수렴).

---

## 🟠 Medium — 여러 도메인에 걸친 구조 정리

### S8. 서비스의 매핑 보일러플레이트를 DTO `of()` 팩토리로 이전
- 현재 `AgriMarketService`, `HydroPowerService`가 `Builder`로 엔티티→DTO를
  수작업 매핑(메서드당 20~50줄).
- 프로젝트 규칙(VO의 정적 `of()` 팩토리)과 동일하게, 매핑 책임을 각 Response/Dto의
  `static of(entity)`로 옮겨 서비스는 "조회 + 위임"만 담당하도록 슬림화.

### S9. 반복 루프 → 스트림 매핑 표준화
- `for` 루프 + `new ArrayList<>()` + `add` 패턴(예: `getDailyPricePrediction`,
  `getMonthlyGeneration`)을 `stream().map(...).toList()`로 통일.
- "리스트 비어있으면 `DATA_NOT_FOUND`" 처리도 공통 헬퍼로 추출 가능.

### S10. `freshfood` 중복 조회 최적화
- `getFreshVegetableIndex`와 `getFreshFruitIndex`가 동일한 `findByBaseDate`를
  각각 호출하고 VO 타입만 다르게 매핑.
- 호출 패턴/매핑 경로를 공통화하고, 한 번의 조회로 처리 가능한지 검토.

### S10b. JPQL 파라미터 바인딩 명명화
- `@Query`의 위치 기반 파라미터(`?1, ?2`)를 `@Param` 기반 명명 파라미터로 전환해
  순서 오류(예: S1 오타 케이스) 가능성 제거.

### S11. 테스트 도입
- 현재 자동화 테스트는 컨텍스트 로드 테스트 1개뿐.
- 우선순위: 서비스 단위 테스트(분류/집계/매핑 로직) → 컨트롤러 슬라이스 테스트
  (`@WebMvcTest` + 검증/예외 응답) → 리포지토리 `@DataJpaTest`.
- `src/test/http/`의 수동 케이스를 자동 테스트로 승격.

### S12. API 문서화(OpenAPI/Swagger)  ✅ 완료 (2026-06-16)
> `springdoc-openapi-starter-webmvc-ui` 도입 → `/swagger-ui.html`, `/v3/api-docs` 자동 노출.
> `OpenApiConfig`로 제목/설명/버전 메타데이터 지정.
- `springdoc-openapi` 도입으로 4개 도메인 엔드포인트 문서 자동화.
- 요청 검증 규칙·응답 스키마·에러 코드 표를 문서에 노출.

---

## 🔴 Hard — 인프라/운영/구조 변경

### S13. 설정·시크릿 외부화  🔸 코드 완료 / 운영 조치 대기 (2026-06-16)
> main `application.yml`을 `${DB_URL/DB_USERNAME/DB_PASSWORD}` 환경변수 placeholder로 전환,
> 테스트는 H2 인메모리로 격리. `.gitignore`에 로컬 시크릿 파일 추가.
> ⚠️ **남은 운영 조치(사용자):** ① 유출된 RDS 자격증명 회전 ② git 히스토리 스크럽(force-push).
- `application.yml`에 DB 접속정보가 평문 하드코딩되어 있던 이력이 있음.
- 환경변수/`.env`/Spring profile(`application-{dev,prod}.yml`)로 분리하고,
  운영 시크릿은 외부 시크릿 매니저(예: 환경변수 주입, Vault 등)로 관리.

### S14. 프로파일 분리 & 구성 관리
- `dev`/`stage`/`prod` 프로파일 분리(로깅 레벨, `show-sql`, DB URL 등).
- 운영에서는 `show-sql: false`, 운영용 커넥션 풀/타임아웃 튜닝.

### S15. 캐싱 전략
- 예측·지수 데이터는 갱신 주기가 길고 조회가 잦은 read-heavy 특성.
- Spring Cache(+필요 시 Redis)로 월/지역/댐 단위 응답 캐시 적용.

### S16. 관측성(Observability)
- 구조적 로깅(JSON), `spring-boot-starter-actuator`(헬스/메트릭),
  요청 추적(correlation id) 도입.
- 기존 `HealthController`를 actuator 헬스 체크로 표준화 검토.

### S17. CI/CD & 품질 게이트
- 빌드/테스트 자동화 파이프라인, 정적분석(Spotless/Checkstyle),
  테스트 커버리지 게이트.

### S18. 영속성·도메인 경계 재검토(선택)
- 현재 엔티티의 연/월/일·지역이 `String`. 도메인 불변식(범위/형식)을
  값 객체(`YearMonth`, `Province` enum 등)로 끌어올려 타입 안전성 강화.
- API 경계의 `String` ↔ 도메인 타입 변환 지점 명확화.

---

## 요약 매트릭스

| ID | 항목 | 난이도 | 영향 범위 | 상태 |
|----|------|--------|-----------|------|
| S1 | 오타 `danName` 수정 | 🟢 | 1 파일 |
| S2 | 포맷/import 정리 | 🟢 | 다수(국소) |
| S3 | `@Transactional(readOnly)` 일관화 | 🟢 | 서비스 |
| S4 | 요청 DTO 검증 통일 | 🟡 | api/*/request | ✅ 완료(이미 충족, 테스트로 확인) |
| S5 | DTO 네이밍 컨벤션 통일 | 🟡 | api 전역 | ✅ 완료(Request/Response) |
| S6 | `ErrorType` 세분화 | 🟡 | support/error | ⏸️ 보류 |
| S7 | 매직 값 상수화 | 🟡 | domain VO | ✅ 완료(FreshFoodGrade) |
| S19 | 등급 임계값 Float/double 비교 정리 | 🟡 | freshfood VO | ✅ 완료 |
| S8 | 매핑을 DTO `of()`로 이전 | 🟠 | api/domain | ✅ 완료(agrimarket/hydropower) |
| S9 | 스트림 매핑 표준화 | 🟠 | 서비스 | ✅ 완료 |
| S10 | freshfood 중복 조회 최적화 | 🟠 | freshfood | 🔸 일부(등급 enum 통합) |
| S10b | JPQL 명명 파라미터 | 🟠 | repository | ✅ 완료 |
| S11 | 테스트 도입 | 🟠 | test 전역 | ✅ 대부분(컨트롤러 4종+서비스 단위, H2) |
| S12 | OpenAPI 문서화 | 🟠 | 빌드/api | ✅ 완료(springdoc) |
| S13 | 시크릿 외부화 | 🔴 | 설정/보안 | 🔸 코드 완료 / 회전·스크럽 대기 |
| S14 | 프로파일 분리 | 🔴 | 설정/운영 | ✅ 완료(dev/prod) |
| S15 | 캐싱 | 🔴 | 인프라/도메인 | ⏸️ 보류 |
| S16 | 관측성 | 🔴 | 인프라/운영 | ✅ 완료(actuator) |
| S17 | CI/CD·품질 게이트 | 🔴 | 빌드/운영 | ✅ 완료(GitHub Actions) |
| S18 | 도메인 타입 안전성 | 🔴 | domain 전역 | ⏸️ 보류 |
