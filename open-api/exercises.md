# 복습 과제 (exercises.md)

> 이 프로젝트를 진행하며 배운 개념·패턴·코드를 **직접 코딩으로 기억하는지** 확인하는 과제 모음입니다.
> 정답을 보지 말고 먼저 풀어 보세요. 막히면 해당 인사이트([insights.md](./insights.md))의 번호를 힌트로 참고.
>
> 표기: ✅ 완료 / ⬜ 미완료 · `[연관: 인사이트/spec ID]` · 난이도 ⭐(쉬움)~⭐⭐⭐(어려움)

---

## Lv.1 — 패턴 읽고 따라 만들기

### E1. ⬜ ApiResponse로 감싸 응답하기 ⭐ `[I1]`
- 새 컨트롤러 메서드 하나를 만들어, 임의의 DTO를 `ApiResponse.success(...)`로 감싸 반환하라.
- **확인 포인트:** 반환 타입이 `ApiResponse<T>`인가? 컨트롤러는 위임만 하는가?

### E2. ⬜ 조회 서비스에 readOnly 트랜잭션 붙이기 ⭐ `[I6 / S3]`
- `FreshFoodService`의 두 조회 메서드에 `@Transactional(readOnly = true)`를 추가하라.
- **확인 포인트:** 왜 조회에 `readOnly=true`를 쓰는지 한 문장으로 설명할 수 있는가?

### E3. ⬜ 요청 DTO에 검증 규칙 추가 ⭐ `[I4 / S4]`
- `FreshFoodIndexRequest`를 본떠, 어떤 요청 record의 `year` 필드에 `@NotBlank` +
  `@Pattern`(2000~2099) 검증을 추가하라. 실패 시 한국어 메시지가 나오게.
- **확인 포인트:** 검증 실패가 `ApiControllerAdvice`의 어느 핸들러로 가는가?

---

## Lv.2 — 리팩터링/패턴 적용

### E4. ⬜ for-루프를 스트림 매핑으로 ⭐⭐ `[I5 / S9]`
- `AgriMarketService.getDailyPricePrediction`의 `for` + `ArrayList` + `add` 블록을
  `stream().map(...).toList()`로 바꿔라. 동작은 동일해야 한다.
- **확인 포인트:** "리스트 비면 `DATA_NOT_FOUND`" 처리는 그대로 유지했는가?

### E5. ⬜ 매핑을 DTO의 static of()로 이전 ⭐⭐ `[I5 / S8]`
- 한 Response DTO에 `static of(엔티티)` 팩토리를 만들고, 서비스의 Builder 매핑을
  그 호출로 대체하라. 서비스는 "조회 + of() 위임"만 남게.
- **확인 포인트:** 서비스 메서드의 줄 수가 줄었는가? 변환 책임이 한 곳에 모였는가?

### E6. ⬜ ErrorType 추가하고 던지기 ⭐⭐ `[I2,I3 / S6]`
- `ErrorType`에 새 항목(예: `INVALID_DATE_RANGE`, 400)을 추가하고,
  서비스 어딘가에서 조건에 맞을 때 `throw new CoreException(...)` 하라.
- **확인 포인트:** advice를 건드리지 않고도 새 에러가 올바른 status/메시지로 응답되는가?

### E11. ⬜ 컨트롤러 검증을 슬라이스 테스트로 증명 ⭐⭐ `[I9 / S11]`
- `FreshFoodApiControllerTest`를 본떠, 다른 컨트롤러(예: HydroPower/AgriMarket)에 대해
  `@WebMvcTest` + `MockMvc`로 (1) 정상 요청 SUCCESS, (2) 잘못된 month 400/E400을 검증하라.
- **확인 포인트:** `@MockBean`으로 서비스를 가짜로 두었는가? 검증 실패가 advice를 거쳐
  `INVALID_PARAMETER` 형식으로 나오는가?

### E12. ⬜ 서비스 단위 테스트 (Mockito) ⭐⭐ `[I9,I10 / S11]`
- `HydroPowerService.getMonthlyGeneration` 등 리스트 매핑 메서드의 단위 테스트를 작성하라
  (리포지토리 `@Mock`). 빈 결과 → `DATA_NOT_FOUND` 케이스도 포함.
- **확인 포인트:** mock을 `thenReturn(...)` 인자 안에 인라인하지 않고 변수로 분리했는가?(I10)

### E7. ⬜ JPQL 위치 파라미터를 명명 파라미터로 ⭐⭐ `[S10b]`
- `HydroPowerRepository`의 `?1, ?2, ?3`을 `:year, :month, :damName` +
  `@Param`으로 바꾸고, 겸사겸사 `danName` 오타도 고쳐라.
- **확인 포인트:** 파라미터 순서 실수가 왜 명명 방식에서 줄어드는가?

---

## Lv.3 — 설계 판단이 필요한 것

### E13. ⬜ Optional.map + of()로 단건 조회 슬림화 ⭐⭐ `[I5,I15 / S8]`
- 단건 조회 서비스 메서드를 `repo.find(...).map(Response::of).orElseThrow(() -> new CoreException(...))`
  한 줄 패턴으로 바꿔라. 리스트 조회는 빈 결과 체크 후 리스트 `of()`에 위임.
- **확인 포인트:** 서비스에 Builder 코드가 더 이상 없는가? 스트림 매핑이 DTO `of()` 안으로 옮겨졌는가?

### E8. ⬜ 시크릿 외부화 ⭐⭐⭐ `[I8 / S13]`
- `application.yml`의 DB username/password를 환경변수 주입 형태
  (`${DB_USERNAME}`, `${DB_PASSWORD}`)로 바꾸고, 로컬 실행 방법을 메모하라.
- **확인 포인트:** 저장소에 평문 자격증명이 더 이상 없는가? 기본값 처리는 어떻게 했는가?

### E9. ⬜ 서비스 단위 테스트 작성 ⭐⭐⭐ `[I4 / S11]`
- `FreshFoodService`의 "등급별 카운트(grouping + counting)" 로직을 검증하는
  단위 테스트를 작성하라(리포지토리는 모킹).
- **확인 포인트:** 테스트가 DB 없이 도는가? 경계 케이스(빈 결과)도 다뤘는가?

### E10. ⬜ 캐싱 적용해 보기 ⭐⭐⭐ `[I7 / S15]`
- 조회가 잦고 잘 안 바뀌는 메서드 하나에 Spring Cache(`@Cacheable`)를 적용하고,
  캐시 키를 무엇으로 잡을지 근거와 함께 정하라.
- **확인 포인트:** 캐시 키에 연/월/지역(또는 댐명)이 모두 반영되는가? 무효화 전략은?

---

## 진행 현황
| 과제 | 상태 | 메모 |
|------|------|------|
| E1~E10 | ⬜ | 풀면 ✅로 바꾸고 배운 점 한 줄 남기기 |

<!-- 새 개념이 쌓이면 적절한 Lv에 과제를 이어서 추가 -->
