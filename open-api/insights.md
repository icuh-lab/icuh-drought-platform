# 인사이트 노트 (insights.md)

> **코딩을 직접 하지 않더라도 알아두면 좋은** 개념·패턴·원칙을 모읍니다.
> 각 항목은 "무엇 / 왜 중요 / 이 프로젝트에서 어디에" 형식으로 짧게 정리합니다.
> 실습으로 확인하려면 [exercises.md](./exercises.md) 참고.

---

## I1. 일관된 응답 래퍼 (`ApiResponse<T>`)
- **무엇:** 모든 API가 `{ result, data, error }` 단일 구조로 응답.
- **왜 중요:** 클라이언트가 성공/실패를 한 가지 규약으로 처리 → 분기 단순화, 계약 안정.
- **이 프로젝트:** `core/support/response/ApiResponse.java`. 성공은 `ApiResponse.success(data)`.

## I2. 전역 예외 처리 (`@RestControllerAdvice`)
- **무엇:** 컨트롤러마다 try/catch 하지 않고, 예외를 한 곳에서 잡아 응답으로 변환.
- **왜 중요:** 에러 응답 형식 일관성 + 비즈니스 코드에서 예외만 "던지면" 됨(관심사 분리).
- **이 프로젝트:** `ApiControllerAdvice`가 `CoreException`/`BindException`/`Exception`을 처리.
  서비스는 `throw new CoreException(ErrorType.DATA_NOT_FOUND)`만 하면 된다.

## I3. 에러 타입의 메타데이터화 (`ErrorType` enum)
- **무엇:** HTTP status, 코드, 메시지, 로그 레벨을 enum 한 곳에 묶어 관리.
- **왜 중요:** 에러 정책이 흩어지지 않고 한눈에 보이며, 로깅 레벨까지 정책으로 강제.
- **이 프로젝트:** `ErrorType`가 `(status, code, message, logLevel)`을 가짐 → advice가 레벨별 로깅.

## I4. 계층 경계와 DTO (엔티티를 API로 직접 노출하지 않기)
- **무엇:** Controller↔Service↔Repository 경계에서 엔티티 대신 Request/Response DTO 사용.
- **왜 중요:** DB 스키마 변경이 API 계약으로 새지 않음(캡슐화), 필요한 필드만 노출(보안).
- **이 프로젝트:** `api/<domain>/{request,response}` DTO. 엔티티는 `domain` 안에만 머문다.

## I5. 정적 팩토리 `of()` 매핑 패턴
- **무엇:** `엔티티 → DTO/VO` 변환을 대상 클래스의 `static of(...)`에 둠.
- **왜 중요:** 변환 책임이 한 곳에 모여 서비스가 가벼워지고, 분류/임계값 같은 도메인 로직을
  VO 안에 캡슐화할 수 있다.
- **이 프로젝트:** `FreshVegetableIndex.of()`, `Sigungu` 등. 서비스의 Builder 매핑도
  이 패턴으로 옮기는 것이 개선안 S8.

## I6. `@Transactional(readOnly = true)`의 의미
- **무엇:** 조회 전용 트랜잭션 힌트. 쓰기 의도가 없음을 선언.
- **왜 중요:** JPA flush/dirty checking 최적화, 의도가 코드로 드러나 안전.
- **이 프로젝트:** 현재 `AgriMarketService`에만 있음 → 전체 조회 메서드에 통일이 개선안 S3.

## I7. 단순→복잡 순의 개선 로드맵 (위험 관리)
- **무엇:** 저위험·고확실성 작업(오타, 포맷, 일관화)을 먼저, 구조·인프라 변경을 나중에.
- **왜 중요:** 초기에 안전한 변경으로 신뢰를 쌓고, 큰 변경 전에 테스트 안전망을 먼저 깐다.
- **이 프로젝트:** `plan.md`의 Phase 0→4. 단, 보안 사안(시크릿 노출)은 예외적으로 조기 처리.

## I8. 시크릿을 코드/설정에 평문으로 두지 않기
- **무엇:** DB 비밀번호 등 민감정보를 `application.yml`에 하드코딩하지 않고 외부 주입.
- **왜 중요:** 저장소 유출 = 자격증명 유출. 환경별 분리/회전이 불가능해짐.
- **이 프로젝트:** 과거 `username`/`password` 평문 노출 이력이 있었으며, 환경변수 외부화와 자격증명 회전이 필요함(개선안 S13).

---

## I9. 두 가지 테스트 슬라이스 — 단위 vs 웹
- **무엇:** 서비스 로직은 `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`로
  리포지토리를 모킹해 **DB 없이** 검증. 컨트롤러는 `@WebMvcTest` + `MockMvc` +
  `@MockBean`으로 HTTP 계층(검증/응답 래퍼/예외)만 빠르게 검증.
- **왜 중요:** DB·전체 컨텍스트(`@SpringBootTest`) 없이 빠르고 안정적으로 도는 테스트를
  만들 수 있다. 각 슬라이스가 책임이 분명해 실패 원인 파악도 쉽다.
- **이 프로젝트:** `FreshFoodServiceTest`(단위), `FreshFoodApiControllerTest`(웹 슬라이스).
  `@WebMvcTest`는 `@RestControllerAdvice`도 로드하므로 검증 실패→`INVALID_PARAMETER` 응답까지 확인 가능.

## I10. Mockito 중첩 stubbing 함정 (`UnfinishedStubbingException`)
- **무엇:** `when(a).thenReturn(makeMock())`처럼 `thenReturn(...)` 인자 안에서 또 다른
  `when().thenReturn()`을 호출하면, 바깥 stubbing이 끝나기 전 안쪽이 끼어들어 예외 발생.
- **왜 중요:** mock 생성 코드를 `thenReturn(...)` 안에 인라인하지 말고, **미리 지역 변수로**
  만들어 분리해야 한다.
- **이 프로젝트:** `FreshFoodServiceTest`에서 `FreshFood` mock을 변수로 빼서 해결.

## I11. Float 필드를 double 리터럴과 비교할 때의 경계 정밀도
- **무엇:** `Float` 값을 `>= 85.1`(double 리터럴)과 비교하면, `Float`가 `double`로 확장되며
  `85.1f → 85.0999...`가 되어 정확한 경계값에서 의도와 다르게 갈릴 수 있다.
- **왜 중요:** 등급/임계값 경계 로직에서 미묘한 분류 오류를 낳는다. 테스트도 경계값을
  그대로 쓰면 깨지므로, 경계에서 떨어진 값으로 검증하거나 비교 타입을 통일해야 한다.
- **이 프로젝트:** `FreshVegetableIndex.createSummary`가 `Float index >= 115.1/.../85.1`로 비교.
  개선안 S19(아래) 후보 — 임계값/비교 타입 정리.

## I12. `@MockBean` → `@MockitoBean` (Spring Boot 3.4+)
- **무엇:** `org.springframework.boot.test.mock.mockito.MockBean`은 3.4부터 deprecated.
  대체는 `org.springframework.test.context.bean.override.mockito.MockitoBean`.
- **왜 중요:** 버전에 맞는 최신 API를 쓰면 경고가 사라지고, 향후 제거에도 안전.
- **이 프로젝트:** Spring Boot 3.5.6 → 슬라이스 테스트의 가짜 빈은 `@MockitoBean` 사용.

## I13. 중복 분기 로직을 enum으로 캡슐화 (임계값→라벨)
- **무엇:** 두 곳에 복붙된 `if (x >= ...) return "..."` 등급 분기를 `FreshFoodGrade` enum
  (임계값+라벨 쌍, `labelOf()`)으로 모음.
- **왜 중요:** 중복 제거 + 매직 넘버 상수화 + 비교 타입 통일(Float/float)을 한 번에. 단위 테스트가
  enum 한 곳으로 집중돼 경계 검증이 쉬워진다.
- **이 프로젝트:** `FreshVegetableIndex`/`FreshFruitIndex`가 `FreshFoodGrade.labelOf(...)` 호출.

## I14. JPQL 명명 파라미터(`@Param`)가 위치 파라미터보다 안전
- **무엇:** `?1, ?2`(위치) 대신 `:year, :damName` + `@Param`(명명)을 쓰면, 인자 순서가
  바뀌어도 이름으로 바인딩돼 실수가 줄어든다.
- **왜 중요:** 파라미터가 많은 쿼리에서 순서 뒤바뀜 버그(과거 `danName` 오타류)를 예방.
- **이 프로젝트:** AgriMarketRepository/HydroPowerRepository 전부 명명 파라미터로 전환.

## I15. `Optional.map(Response::of).orElseThrow(...)` 패턴
- **무엇:** 단건 조회 시 `repo.find(...).map(Dto::of).orElseThrow(() -> new CoreException(...))`로
  "있으면 매핑, 없으면 예외"를 한 줄로.
- **왜 중요:** 서비스가 조회·매핑·예외만 선언적으로 표현 → 읽기 쉽고 의도가 분명.
- **이 프로젝트:** HydroPowerService의 단건 조회 메서드들. 리스트 조회는 `isEmpty()` 체크 후
  리스트 of()에 위임(스트림 매핑은 DTO 안에서).

## I16. 시크릿 외부화 + 테스트는 H2로 격리
- **무엇:** 설정 파일엔 `${DB_PASSWORD}` 같은 환경변수 placeholder만 두고 실제 값은 런타임 주입.
  테스트는 H2 인메모리(`jdbc:h2:mem`, `ddl-auto=create-drop`)로 외부 DB·자격증명 의존을 제거.
- **왜 중요:** 저장소에 평문 시크릿이 남지 않고, 테스트가 빠르고 어디서나 재현 가능해진다.
- **이 프로젝트:** main `application.yml`은 env placeholder, `src/test/resources/application.yml`은 H2.
  테스트 클래스패스의 application.yml이 main을 덮어써 `${DB_URL}`가 테스트에서 평가되지 않음.

## I17. 시크릿이 git 히스토리에 들어가면 파일 수정만으론 부족
- **무엇:** 한번 커밋·푸시된 비밀번호는 현재 파일을 고쳐도 **과거 커밋/원격/포크/캐시에 그대로** 남는다.
- **왜 중요:** 진짜 해결은 ① **자격증명 회전(무력화)** 이 최우선, ② 필요 시 히스토리 스크럽
  (git-filter-repo/BFG + force-push, 공유 저장소면 협업자 재클론). 회전 없는 스크럽은 불완전.
- **이 프로젝트:** 팀 저장소(icuh-lab)에 RDS 자격증명이 노출 → 회전이 가장 시급.

## I18. springdoc-openapi로 API 문서 자동화
- **무엇:** `springdoc-openapi-starter-webmvc-ui` 의존성만 추가하면 컨트롤러 시그니처를 읽어
  `/swagger-ui.html`과 `/v3/api-docs`(OpenAPI JSON)를 자동 생성.
- **왜 중요:** 코드와 문서가 어긋나지 않고, 팀/프론트가 엔드포인트·검증·응답 스키마를 바로 확인.
- **이 프로젝트:** `OpenApiConfig`로 메타데이터만 지정. 버전은 Spring Boot에 맞춰 선택(3.5.x ↔ 2.8.x).
  참고: `@WebMvcTest` 슬라이스는 `@Configuration`(OpenApiConfig)·springdoc 자동설정을 로드하지 않으므로
  슬라이스 테스트엔 영향 없음(전체 컨텍스트 테스트에서만 로드).

## I19. Spring 프로파일로 환경별 설정 분리
- **무엇:** 공통은 `application.yml`, 환경 차이는 `application-{dev,prod}.yml`로 분리하고
  `SPRING_PROFILES_ACTIVE`(또는 `spring.profiles.default`)로 활성화.
- **왜 중요:** 운영에서 `show-sql:false`·보수적 로깅·풀 튜닝을, 개발에서 디버깅 편의를 각각 적용.
  설정이 환경별로 명확히 갈려 사고를 줄인다.
- **이 프로젝트:** dev=SQL/디버그 로그, prod=SQL off+Hikari 풀+WARN. 테스트는 test resources의
  application.yml(H2)이 main을 shadow하므로 프로파일과 무관하게 격리됨.

## I20. 테스트가 H2면 CI에 DB/시크릿이 필요 없다
- **무엇:** `./gradlew build`는 앱을 띄우지 않고 컴파일+테스트만 수행. 테스트가 H2 인메모리면
  외부 DB·자격증명 없이 CI에서 그대로 통과.
- **왜 중요:** CI 파이프라인이 단순해지고(서비스 컨테이너 불필요), 시크릿 노출 위험도 없음.
- **이 프로젝트:** `.github/workflows/ci.yml`이 setup-java(17) 후 `./gradlew build`만 실행.

<!-- 새 인사이트는 I번호를 이어서 아래에 추가 -->
