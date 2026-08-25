# 개발 로그 (dev-log.md)

> 이 프로젝트(icuh-platform-api)를 진행하며 나눈 내용을 **시간순**으로 누적 기록합니다.
> 개발 관련 내용을 중심으로 적되, 결정·이유·다음 액션을 함께 남깁니다.
> 짝꿍 파일: [insights.md](./insights.md)(기억할 개념), [exercises.md](./exercises.md)(복습 과제), [spec.md](./spec.md), [plan.md](./plan.md)

---

## 2026-06-16

### 1) 프로젝트 구조 분석
- **요청:** 현재 프로젝트 구조 분석.
- **내용:**
  - 스택: Spring Boot 3.5.6 / Java 17 / Spring Data JPA / MySQL(`ACTUAL_DRGHT`, `ddl-auto: none`).
  - 패키지: `core/{api, domain, support}` — 레이어드 + 도메인별 수직 분할.
  - 4개 도메인: `agrimarket`, `freshfood`, `hydropower`, `wildfire`.
  - 핵심 패턴: `ApiResponse<T>` 응답 래퍼, `ApiControllerAdvice` 전역 예외처리,
    도메인 VO의 정적 `of()` 팩토리, `@Query` JPQL 다중 엔티티 리포지토리.
- **관찰된 문제:** DB 자격증명 평문 노출, `danName` 오타, 테스트 부재, `ErrorType` 빈약.

### 2) 개선 포인트 정리 → spec.md / plan.md
- **요청:** 개선 포인트를 단순→복잡 순으로 `spec.md`에, Phase로 나눠 `plan.md`에 작성.
- **결과:**
  - `spec.md`: S1~S18을 난이도순(🟢→🔴)으로 정리 + 요약 매트릭스.
  - `plan.md`: Phase 0(정리) → 1(API 계약) → 2(서비스 슬림화) → 3(테스트·문서) → 4(운영).
  - 보안 사안 S13(시크릿 평문 노출)은 Phase를 기다리지 말고 조기 처리 권장.

### 3) 학습 운영 체계 도입
- **요청:** 앞으로의 대화를 기록하고, 개발 내용은 별도 md로, 인사이트는 별도 파일에 축적,
  그리고 배운 개념을 직접 코딩으로 확인할 복습 과제를 만들어 달라.
- **결과:** `dev-log.md`(본 파일) / `insights.md` / `exercises.md` 생성, 운영 방식 메모리 저장.
- **다음 액션:** 합의되면 Phase 0(S1 오타, S3 트랜잭션 일관화)부터 착수.

---

### 4) Phase 0 적용 (Quick Wins)
- **요청:** plan.md의 Phase 0부터 진행.
- **변경(5개 파일):**
  - S1 — `HydroPowerRepository`의 파라미터 오타 `danName` → `damName`.
  - S2 — `HydroPowerService` 클래스 끝 중복 빈 줄 제거.
  - S3 — 조회 서비스 4종(`FreshFood/HydroPower/WildFire/AgriMarket`)에
    `@Transactional(readOnly = true)`를 **클래스 레벨**로 통일.
    (AgriMarket은 기존 메서드 레벨 3개 → 클래스 레벨 1개로 전환)
- **검증:** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build` → BUILD SUCCESSFUL(테스트 포함).
- **환경 메모:** 시스템 기본 JDK가 25라 Gradle 실행 시 Java 17 `JAVA_HOME` 지정 필요(이 환경 특이사항).
- **다음 액션:** Phase 1(S4 검증 통일, S5 네이밍, S6 ErrorType 세분화, S7 상수화).

---

### 5) 테스트 워크플로 도입 + 첫 테스트 작성
- **요청:** 이제부터 각 단계마다 테스트 코드도 함께 작성해 정상 동작 확인.
- **발견:** 요청 DTO 4종 모두 이미 `@NotBlank`+`@Pattern` 검증을 갖춤 → **S4는 사실상 완료**,
  필요한 건 "검증이 동작함을 증명하는 테스트". spec의 가정 정정.
- **작성한 테스트(첫 실 테스트):**
  - `FreshFoodServiceTest` — Mockito로 리포지토리 모킹, baseDate 변환/등급 분류/등급별 집계 검증.
  - `FreshFoodApiControllerTest` — `@WebMvcTest`로 정상(SUCCESS 래퍼)·검증실패(400/E400) 응답 검증.
- **디버깅 중 배운 점:**
  - `UnfinishedStubbingException` — `thenReturn(...)` 인자 안에서 mock을 또 stubbing해 발생 → 변수 분리(I10).
  - `Float` vs `double` 리터럴 경계 정밀도 — `85.1f`가 `85.0999..`로 확장돼 경계 분류가 어긋남(I11).
- **검증:** 두 테스트 클래스 6개 전부 통과(`./gradlew test --tests ...`).
- **신규 개선 후보:** S19 — 등급 임계값 비교의 `Float`/`double` 타입 정리(잠재 분류 오류).

---

### 6) Phase 1 — S7+S19 (등급 분류 enum 추출)
- **변경:** 두 VO에 복붙돼 있던 `createSummary` 등급 분기를 `FreshFoodGrade` enum으로 추출.
  - S19: 임계값을 `float` 리터럴로 두어 `Float` 지수와 같은 타입 비교(경계 정밀도 해결).
  - S7: 매직 넘버/라벨 상수화. S10 일부(중복 제거)도 함께 해결.
- **테스트:** `FreshFoodGradeTest`(파라미터라이즈드, 경계값 115.1/85.1 포함) 추가 → 통과.
  기존 `FreshFoodServiceTest`/`FreshFoodApiControllerTest`도 그대로 통과.
- **부수 개선:** 슬라이스 테스트의 `@MockBean`(deprecated) → `@MockitoBean`으로 교체(I12).
- **결정:** S5(네이밍 통일)·S6(ErrorType 세분화) 모두 **보류**.
  S5는 순수 cosmetic·큰 diff, S6은 실제 throw 지점 없는 죽은 enum 우려. 필요 단계에서 재개.
- **Phase 1 종료:** S4(이미 충족, 테스트로 확인)·S7·S19 완료, S5·S6 보류. → 다음은 Phase 2.

---

### 7) Phase 1 — S5 네이밍 통일 + 커밋 워크플로 도입
- **요청:** DTO 네이밍을 전부 `Request`/`Response`로 통일, 각 Phase 종료 시 git commit(정리된 메시지).
- **S5 변경:** agrimarket/hydropower의 `~Dto` 20개 클래스를 `~Request`/`~Response`로 리네이밍.
  - 타입명만 변경하고 **JSON 필드명(응답 키)은 보존**(API 계약 유지).
  - 단어 경계 치환(perl)으로 필드명/변수명 보호 → `git mv`로 파일명 변경 → 빌드·테스트 통과.
- **워크플로:** Phase 종료 시 커밋 규칙 합의(메모리 저장). 이 저장소는 main 직접 커밋 관행.
- **커밋:** Phase 0(정리·일관화)와 Phase 1(검증 테스트·등급 enum·DTO 리네이밍)은 공유 파일에
  변경이 얽혀 있어 한 커밋으로 묶어 기록. Phase 2부터는 단계별 단일 커밋.
- **Phase 1 종료:** S4·S5·S7·S19 완료, S6 보류. → 다음 Phase 2.

---

### 8) Phase 2 — 서비스 슬림화 & 쿼리 정리
- **S8:** agrimarket/hydropower의 모든 응답 DTO에 `static of(entity)` 팩토리 추가.
  중첩 DTO도 각자 of()를 갖고, 상위 of()가 이를 조합 → 서비스의 Builder 매핑 보일러플레이트 제거.
- **S9:** 서비스의 `for`+`ArrayList`+`add` → 리스트 of()가 `stream().map(of).toList()`로 처리.
  AgriMarketService/HydroPowerService가 "조회 + of() 위임 (+빈 결과 DATA_NOT_FOUND)"만 남도록 슬림화.
  - HydroPower 단건 조회는 `Optional.map(Response::of).orElseThrow(...)` 패턴으로 간결화.
- **S10b:** 두 리포지토리의 위치 파라미터(`?1,?2,?3`) → `@Param` 명명 파라미터로 전환.
- **테스트:** `AgriMarketServiceTest`, `HydroPowerServiceTest`(Mockito) 신규 — 매핑/중첩/빈 결과
  DATA_NOT_FOUND 검증. 전체 테스트 통과.
- **Phase 2 종료:** S8·S9·S10b 완료, S10·S11 부분 진행. → 다음 Phase 3(테스트·문서) 또는 Phase 4.

---

### 9) Phase 4 선반영 — S13 시크릿 외부화 (보안 우선)
- **발견:** 커밋된 application.yml(HEAD/히스토리)에 실제 RDS 자격증명(dasom/dasom123 + RDS 호스트)
  노출. 원격은 GitHub 팀 저장소(icuh-lab/icuh-platform-api) → 유출 범위 큼.
- **결정(사용자):** ① 자격증명 회전 + 히스토리 스크럽 ② main yml은 환경변수, 테스트는 H2.
- **코드 변경:**
  - main `application.yml`: `url/username/password` → `${DB_URL}/${DB_USERNAME}/${DB_PASSWORD}`.
  - `src/test/resources/application.yml`: H2 인메모리(MODE=MySQL, ddl-auto=create-drop)로 테스트 격리.
  - `build.gradle`: `testRuntimeOnly com.h2database:h2`.
  - `.gitignore`: `.env`, `application-local.yml` 등 로컬 시크릿 파일 차단.
- **검증:** 전체 테스트 H2로 통과(로컬 MySQL/실 자격증명 불필요).
- **남은 운영 조치(사용자):** RDS 비밀번호 회전(AWS), git 히스토리 스크럽(git-filter-repo + force-push,
  팀 협업자 재클론 필요) — 별도 확인 후 진행.
- **결정:** 스크럽은 **자격증명 회전 + 팀 공지 후** 진행(지금 force-push 안 함).
- **스크럽 런북(준비되면 실행):**
  ```bash
  # 0) 선행: RDS 비번 회전 완료 + 팀원 공지(이후 모두 재클론 필요)
  brew install git-filter-repo            # 또는: pip install git-filter-repo
  # 노출 문자열을 히스토리 전체에서 치환(파일에 정의)
  printf 'dasom123==>REMOVED\ndasom==>REMOVED\nicuh-rds.cd7bwwfid5u3.ap-northeast-2.rds.amazonaws.com==>REMOVED\n' > ../secrets-replace.txt
  git filter-repo --replace-text ../secrets-replace.txt
  git remote add origin git@github.com:icuh-lab/icuh-platform-api.git  # filter-repo가 origin 제거함
  git push --force --all && git push --force --tags
  ```

---

### 10) Phase 3 — 테스트 확대(S11) + API 문서화(S12)
- **S12:** `springdoc-openapi-starter-webmvc-ui:2.8.6` 도입 + `OpenApiConfig`(제목/설명/버전).
  → `/swagger-ui.html`, `/v3/api-docs` 자동 생성. Spring Boot 3.5.6와 호환 확인(컨텍스트 로드 통과).
- **S11:** 컨트롤러 슬라이스 테스트 3종 추가(agrimarket/hydropower/wildfire) — 정상 SUCCESS +
  잘못된 month/누락 필드 → 400/E400. 4개 도메인 모두 검증 계약 고정.
- **참고:** 일부 엔드포인트(hydropower `monthly-reservoir`, wildfire `forecast`)는 `@Valid` 누락 →
  검증 미적용. 동작은 유지하고 테스트는 실제 동작 기준으로 작성(추후 보강 후보).
- **검증:** 전체 테스트(H2) 통과.
- **Phase 3 종료:** S11 대부분·S12 완료. → 다음 Phase 4(S14 프로파일, S15 캐싱, S16 관측성, S17 CI, S18 타입).

---

### 11) Phase 4 — 프로파일·관측성·CI (S14/S16/S17)
- **결정(사용자):** Phase 4는 S14·S16·S17만 진행, S15(캐싱)·S18(타입안전성)은 보류.
- **S14 프로파일 분리:** 공통 `application.yml`(name, datasource(env), 공통 JPA, management) +
  `application-dev.yml`(show-sql/디버그 로그) + `application-prod.yml`(show-sql off, Hikari 풀, WARN 로깅).
  `spring.profiles.default: dev`로 로컬 기본 dev. (테스트는 test resources의 H2 yml이 shadow → 프로파일 미적용)
- **S16 관측성:** `spring-boot-starter-actuator` 추가, `/actuator/health,info` 노출.
- **S17 CI:** `.github/workflows/ci.yml` — push/PR(main)에서 JDK17 setup 후 `./gradlew build`.
  테스트가 H2라 외부 DB/시크릿 없이 CI 통과 가능.
- **검증:** `./gradlew build`(CI와 동일) 통과.
- **Phase 4 종료:** S13·S14·S16·S17 완료. 남은 보류: S6·S15·S18, S13 운영조치(회전/스크럽).

---

<!-- 새 항목은 위 형식(날짜 ▸ 주제 ▸ 요청/내용/결정/다음 액션)으로 아래에 계속 추가 -->
