# drought 도메인 구현 계획 (open-api)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `drought_impact_report`(Python)가 운영 MySQL에 적재하는 월간 가뭄영향 리포트 4개 테이블을
`open-api`에서 읽어 목록/상세 API로 노출하고, 기존 `/v1/summary` 스텁에 drought 알림을 최소 기여한다.

**Architecture:** 기존 `wildfire` 도메인과 동일한 3계층 패턴(entity+repository in `core-persistence`,
service+response DTO in `core-application`, controller in `open-api`)을 따르되, Python 쪽 4개 테이블이
전부 복합 자연키라 이 저장소 최초로 `@IdClass`를 쓴다. 등급 enum(`ReportGrade`)과 영향분야 enum
(`DroughtImpactField`)은 엔티티가 참조해야 하므로 `core-domain`에 둔다(모듈 의존 방향: `core-persistence`는
`core-application`을 의존하지 않는다 — article 도메인의 `ArticleStatus`가 정확히 이 이유로 `core-domain`에
있는 전례를 따른다).

**Tech Stack:** Spring Boot 3.5.6 · Java 17 · Spring Data JPA(MySQL, `ddl-auto: none`) · Lombok ·
JUnit5 + AssertJ + Mockito · `@DataJpaTest`(H2) / `@WebMvcTest`.

**Spec:** [docs/superpowers/specs/2026-08-30-drought-report-domain-design.md](../specs/2026-08-30-drought-report-domain-design.md)

## Global Constraints

- 패키지 루트는 `re.kr.icuh.drought`. 신규 코드는 `persistence.openapi.drought`(core-persistence),
  `application.openapi.drought`(core-application), `openapi.core.api.drought`(open-api)에 둔다 —
  `wildfire` 형제 도메인과 동일한 트리 모양.
- 모듈 의존 방향은 단방향이다: `core-persistence → common, core-domain`;
  `core-application → common, core-domain, core-persistence`;
  `open-api → common, core-application, core-persistence`. 엔티티가 참조하는 것(예: `@Enumerated` 컬럼의
  enum)은 반드시 `core-domain`에 둔다. 절대 `core-application`에 두지 않는다.
- 엔드포인트 기본 경로는 `/api/v1/drought` — 기존 모든 open-api 도메인이 쓰는
  `@RequestMapping("/api/v1/{domain}")` 컨벤션을 그대로 따른다.
- 에러 처리는 `re.kr.icuh.drought.common.openapi.error.CoreException` +
  `re.kr.icuh.drought.common.openapi.error.ErrorType.DATA_NOT_FOUND`만 쓴다. 새 `ErrorType`을
  추가하거나 `public-api`의 `BusinessException`/`ErrorCode`를 쓰지 않는다.
- 컨트롤러는 전부 `re.kr.icuh.drought.common.openapi.response.ApiResponse<T>`를
  `ApiResponse.success(data)`로 감싸 반환한다.
- 응답 DTO는 전부 `...Response`로 끝나는 Java record이고, 정적 팩토리 메서드 이름은 `of(...)`다
  (`fromEntity`/`from` 아님).
- 등급 이름은 정확히 `관심`, `주의`, `경계`, `심각`이고 이 순서로 선언한다(`ReportGrade` enum,
  ordinal이 곧 심각도 비교 기준). 이 저장소의 다른 곳에 있는 기존 대시보드 등급 체계(관심/주의/경고/위험)와
  절대 혼동하지 않는다 — 이름이 겹치는 게 하나도 없다는 걸 코드 리뷰에서 재확인한다.
- Python 쪽이 저장하는 실제 `sido`/`sigungu` 값은 축약형이다(`강원도`가 아니라 `강원`, `강릉시`가
  아니라 `강릉`). DB 값을 그대로 통과시키고, 이 도메인의 어떤 코드도 접미사를 붙이거나 다듬지 않는다.
- `/v1/summary` 기여분의 `regionCode`는 **항상 `null`**이다. 이름→코드 룩업 테이블을 만들지 않는다
  (근거는 스펙 §6.3 "regionCode 정정" 참조).
- `ddl-auto: none` — Hibernate가 실제 MySQL 스키마를 절대 건드리지 않는다. `@DataJpaTest`가 쓰는
  H2는 그 테스트 슬라이스 전용 임시 스키마라 무관하다(Hibernate가 자동 생성해도 안전).
- 각 태스크 종료 시 저장소 루트에서 `./gradlew clean compileJava compileTestJava --console=plain`
  으로 컴파일을 확인하고, 해당 모듈의 `./gradlew :<module>:test --console=plain`로 테스트를 돌린다.

---

## Task 1: `ReportGrade` / `DroughtImpactField` enum (core-domain)

**Files:**
- Create: `core-domain/src/main/java/re/kr/icuh/drought/domain/drought/ReportGrade.java`
- Create: `core-domain/src/main/java/re/kr/icuh/drought/domain/drought/DroughtImpactField.java`
- Test: `core-domain/src/test/java/re/kr/icuh/drought/domain/drought/ReportGradeTest.java`
- Test: `core-domain/src/test/java/re/kr/icuh/drought/domain/drought/DroughtImpactFieldTest.java`

**Interfaces:**
- Produces: `ReportGrade` — 상수 `관심, 주의, 경계, 심각`을 이 순서로 선언(ordinal 0~3 = 심각도).
  enum은 기본으로 `Comparable`이므로 이후 태스크는 `Collections.max`/`Comparator.naturalOrder()`를
  바로 쓴다.
- Produces: `DroughtImpactField` — 상수 `A1..A8`, 인스턴스 메서드 `displayName()`(정확한 한글명),
  정적 메서드 `fromCode(String code)`(모르는 코드면 `IllegalArgumentException`).

- [ ] **Step 1: `ReportGrade` 실패하는 테스트 작성**

```java
package re.kr.icuh.drought.domain.drought;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportGradeTest {

    @Test
    @DisplayName("등급 순서는 관심 < 주의 < 경계 < 심각이다")
    void ordinalOrderMatchesSeverity() {
        assertThat(ReportGrade.관심).isLessThan(ReportGrade.주의);
        assertThat(ReportGrade.주의).isLessThan(ReportGrade.경계);
        assertThat(ReportGrade.경계).isLessThan(ReportGrade.심각);
    }

    @Test
    @DisplayName("여러 등급 중 최댓값은 Comparable 순서로 구해진다")
    void maxPicksTheMostSevereGrade() {
        assertThat(Collections.max(List.of(ReportGrade.주의, ReportGrade.심각, ReportGrade.관심)))
                .isEqualTo(ReportGrade.심각);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :core-domain:test --tests "re.kr.icuh.drought.domain.drought.ReportGradeTest" --console=plain`
Expected: FAIL — `ReportGrade`가 존재하지 않아 컴파일 실패.

- [ ] **Step 3: `ReportGrade` 구현**

```java
package re.kr.icuh.drought.domain.drought;

public enum ReportGrade {
    관심,
    주의,
    경계,
    심각
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :core-domain:test --tests "re.kr.icuh.drought.domain.drought.ReportGradeTest" --console=plain`
Expected: PASS

- [ ] **Step 5: `DroughtImpactField` 실패하는 테스트 작성**

```java
package re.kr.icuh.drought.domain.drought;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DroughtImpactFieldTest {

    @Test
    @DisplayName("A1~A8 코드는 실제 impact_field 테이블의 한글명과 정확히 일치한다")
    void displayNameMatchesRealData() {
        assertThat(DroughtImpactField.A1.displayName()).isEqualTo("물 공급");
        assertThat(DroughtImpactField.A2.displayName()).isEqualTo("농업");
        assertThat(DroughtImpactField.A3.displayName()).isEqualTo("축산업");
        assertThat(DroughtImpactField.A4.displayName()).isEqualTo("수산업");
        assertThat(DroughtImpactField.A5.displayName()).isEqualTo("산업");
        assertThat(DroughtImpactField.A6.displayName()).isEqualTo("환경");
        assertThat(DroughtImpactField.A7.displayName()).isEqualTo("사회경제");
        assertThat(DroughtImpactField.A8.displayName()).isEqualTo("기타");
    }

    @Test
    @DisplayName("fromCode는 코드 문자열로 enum 상수를 찾는다")
    void fromCodeResolvesByName() {
        assertThat(DroughtImpactField.fromCode("A3")).isEqualTo(DroughtImpactField.A3);
    }

    @Test
    @DisplayName("모르는 코드는 IllegalArgumentException을 던진다")
    void fromCodeRejectsUnknownCode() {
        assertThatThrownBy(() -> DroughtImpactField.fromCode("Z9"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

> 참고: 위 8개 한글명은 Python 쪽 운영 데이터를 실측(2026-08-30, `impact_field` 테이블 SELECT)해
> 확인한 실제 값이다. 지어낸 이름을 쓰지 않는다.

- [ ] **Step 6: 실패 확인**

Run: `./gradlew :core-domain:test --tests "re.kr.icuh.drought.domain.drought.DroughtImpactFieldTest" --console=plain`
Expected: FAIL — `DroughtImpactField`가 존재하지 않아 컴파일 실패.

- [ ] **Step 7: `DroughtImpactField` 구현**

```java
package re.kr.icuh.drought.domain.drought;

public enum DroughtImpactField {
    A1("물 공급"),
    A2("농업"),
    A3("축산업"),
    A4("수산업"),
    A5("산업"),
    A6("환경"),
    A7("사회경제"),
    A8("기타");

    private final String displayName;

    DroughtImpactField(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static DroughtImpactField fromCode(String code) {
        for (DroughtImpactField field : values()) {
            if (field.name().equals(code)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unknown drought impact code: " + code);
    }
}
```

- [ ] **Step 8: 통과 확인**

Run: `./gradlew :core-domain:test --console=plain`
Expected: PASS (core-domain 전체)

- [ ] **Step 9: 커밋**

```bash
git add core-domain/src/main/java/re/kr/icuh/drought/domain/drought core-domain/src/test/java/re/kr/icuh/drought/domain/drought
git commit -m "feat(drought): 리포트 등급·영향분야 enum 추가"
```

---

## Task 2: JPA 엔티티 + `@IdClass` + `KeywordsJsonConverter` (core-persistence)

**Files:**
- Create: `core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought/converter/KeywordsJsonConverter.java`
- Create: `core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought/entity/DroughtMonthlyReport.java`
- Create: `core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought/entity/DroughtMonthlyReportBucketId.java`
- Create: `core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought/entity/DroughtMonthlyReportBucket.java`
- Create: `core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought/entity/DroughtMonthlyReportSidoStatusId.java`
- Create: `core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought/entity/DroughtMonthlyReportSidoStatus.java`

**Interfaces:**
- Consumes: `re.kr.icuh.drought.domain.drought.ReportGrade`(Task 1).
- Produces: 3개 엔티티 + 2개 `@IdClass`. `DroughtMonthlyReportBucket`/`DroughtMonthlyReportSidoStatus`는
  `@Builder`로 생성한다(Task 3~4가 테스트에서 이 빌더로 인스턴스를 만든다) — `Article` 엔티티와 동일한
  `@Getter @Builder @NoArgsConstructor(PROTECTED) @AllArgsConstructor` 조합.

이 태스크는 엔티티 자체를 단위 테스트하지 않는다(Article/wildfire 전례와 동일 — 엔티티는 얇은 매핑이라
Task 3의 `@DataJpaTest`가 저장/조회를 통해 간접 검증한다). 대신 컴파일 확인으로 완료 기준을 삼는다.

- [ ] **Step 1: `KeywordsJsonConverter` 작성**

`article` 도메인의 `UpdateArticleRequestJsonConverter` 패턴을 그대로 따른다.

```java
package re.kr.icuh.drought.persistence.openapi.drought.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Converter
public class KeywordsJsonConverter implements AttributeConverter<List<String>, String> {

    private final ObjectMapper objectMapper;

    public KeywordsJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (ObjectUtils.isEmpty(attribute)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (StringUtils.hasText(dbData)) {
            try {
                return objectMapper.readValue(dbData, new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: `DroughtMonthlyReport` 엔티티 작성 (단순 PK)**

```java
package re.kr.icuh.drought.persistence.openapi.drought.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "drought_monthly_report")
public class DroughtMonthlyReport {

    @Id
    @Column(name = "report_ym", length = 7)
    private String reportYm;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "article_count", nullable = false)
    private int articleCount;

    @Column(name = "detected_sido_count", nullable = false)
    private int detectedSidoCount;
}
```

- [ ] **Step 3: `DroughtMonthlyReportBucketId` 작성**

```java
package re.kr.icuh.drought.persistence.openapi.drought.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DroughtMonthlyReportBucketId implements Serializable {
    private String reportYm;
    private String sido;
    private String sigungu;
    private String impactCode;
}
```

- [ ] **Step 4: `DroughtMonthlyReportBucket` 엔티티 작성 (`@IdClass`, JSON 컬럼)**

```java
package re.kr.icuh.drought.persistence.openapi.drought.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import re.kr.icuh.drought.domain.drought.ReportGrade;
import re.kr.icuh.drought.persistence.openapi.drought.converter.KeywordsJsonConverter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "drought_monthly_report_bucket")
@IdClass(DroughtMonthlyReportBucketId.class)
public class DroughtMonthlyReportBucket {

    @Id
    @Column(name = "report_ym", length = 7)
    private String reportYm;

    @Id
    @Column(name = "sido", length = 20)
    private String sido;

    @Id
    @Column(name = "sigungu", length = 30)
    private String sigungu;

    @Id
    @Column(name = "impact_code", length = 2)
    private String impactCode;

    @Column(name = "article_count", nullable = false)
    private int articleCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false)
    private ReportGrade grade;

    @Column(name = "grade_finalized_at")
    private LocalDateTime gradeFinalizedAt;

    @Column(name = "representative_link", length = 700)
    private String representativeLink;

    @Column(name = "representative_title", length = 500)
    private String representativeTitle;

    @Convert(converter = KeywordsJsonConverter.class)
    @Column(name = "keywords", columnDefinition = "TEXT")
    private List<String> keywords;

    @Column(name = "relevance_flag", nullable = false)
    private boolean relevanceFlag;

    @Column(name = "continuity_count", nullable = false)
    private int continuityCount;
}
```

> `columnDefinition = "TEXT"`는 `@DataJpaTest`의 H2가 자동 생성하는 테스트 전용 스키마에만 영향을
> 준다(`ddl-auto: none`이라 실제 MySQL `JSON` 컬럼은 건드리지 않는다) — 기본 `varchar(255)`로 생성돼
> 키워드 목록이 잘리는 걸 미리 막는다.

- [ ] **Step 5: `DroughtMonthlyReportSidoStatusId` 작성**

```java
package re.kr.icuh.drought.persistence.openapi.drought.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DroughtMonthlyReportSidoStatusId implements Serializable {
    private String reportYm;
    private String sido;
}
```

- [ ] **Step 6: `DroughtMonthlyReportSidoStatus` 엔티티 작성 (`@IdClass`)**

```java
package re.kr.icuh.drought.persistence.openapi.drought.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import re.kr.icuh.drought.domain.drought.ReportGrade;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "drought_monthly_report_sido_status")
@IdClass(DroughtMonthlyReportSidoStatusId.class)
public class DroughtMonthlyReportSidoStatus {

    @Id
    @Column(name = "report_ym", length = 7)
    private String reportYm;

    @Id
    @Column(name = "sido", length = 10)
    private String sido;

    @Column(name = "detected", nullable = false)
    private boolean detected;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_grade")
    private ReportGrade maxGrade;
}
```

- [ ] **Step 7: 컴파일 확인**

Run: `./gradlew :core-persistence:compileJava --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 커밋**

```bash
git add core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought
git commit -m "feat(drought): 월간 리포트 엔티티 3종 + keywords JSON 컨버터 추가"
```

---

## Task 3: 리포지토리 + `@DataJpaTest` (core-persistence)

**Files:**
- Create: `core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought/repository/DroughtMonthlyReportRepository.java`
- Create: `core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought/repository/DroughtMonthlyReportBucketRepository.java`
- Create: `core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought/repository/DroughtMonthlyReportSidoStatusRepository.java`
- Test: `core-persistence/src/test/java/re/kr/icuh/drought/persistence/openapi/drought/repository/DroughtReportRepositoriesTest.java`

**Interfaces:**
- Consumes: 3개 엔티티(Task 2).
- Produces: `DroughtMonthlyReportRepository.findTopByOrderByReportYmDesc(): Optional<DroughtMonthlyReport>`,
  `DroughtMonthlyReportBucketRepository.findByReportYm(String): List<DroughtMonthlyReportBucket>`,
  `DroughtMonthlyReportSidoStatusRepository.findByReportYm(String): List<DroughtMonthlyReportSidoStatus>`,
  `DroughtMonthlyReportSidoStatusRepository.findByReportYmAndDetectedTrue(String): List<DroughtMonthlyReportSidoStatus>`.
  Task 4의 `DroughtReportService`가 이 4개 메서드 + 상속받은 `findAll(Pageable)`/`findById(String)`을 쓴다.

- [ ] **Step 1: 실패하는 `@DataJpaTest` 작성**

`core-persistence`의 유일한 기존 슬라이스 테스트(`PersistenceSliceTest`)와 동일한 뼈대
(`@EntityScan`/`@EnableJpaRepositories(basePackages="re.kr.icuh.drought.persistence")` +
`ObjectMapper` 빈을 제공하는 내부 `TestApplication`)를 그대로 재사용한다.

```java
package re.kr.icuh.drought.persistence.openapi.drought.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.fasterxml.jackson.databind.ObjectMapper;
import re.kr.icuh.drought.domain.drought.ReportGrade;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReport;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan(basePackages = "re.kr.icuh.drought.persistence")
@EnableJpaRepositories(basePackages = "re.kr.icuh.drought.persistence")
class DroughtReportRepositoriesTest {

    @Autowired
    private DroughtMonthlyReportRepository reportRepository;
    @Autowired
    private DroughtMonthlyReportBucketRepository bucketRepository;
    @Autowired
    private DroughtMonthlyReportSidoStatusRepository sidoStatusRepository;

    @Test
    @DisplayName("findTopByOrderByReportYmDesc는 가장 최신 연월을 반환한다")
    void findsLatestReport() {
        reportRepository.save(report("2026-04", 100, 5));
        reportRepository.save(report("2026-06", 200, 10));
        reportRepository.save(report("2026-05", 150, 8));

        assertThat(reportRepository.findTopByOrderByReportYmDesc())
                .isPresent()
                .get()
                .extracting(DroughtMonthlyReport::getReportYm)
                .isEqualTo("2026-06");
    }

    @Test
    @DisplayName("복합키(report_ym+sido+sigungu+impact_code)로 버킷을 저장하고 report_ym으로 조회한다")
    void savesAndFindsBucketsByReportYm() {
        reportRepository.save(report("2026-05", 748, 16));
        bucketRepository.save(bucket("2026-05", "강원", "강릉", "A1", ReportGrade.심각));
        bucketRepository.save(bucket("2026-05", "강원", "강릉", "A3", ReportGrade.경계));

        assertThat(bucketRepository.findByReportYm("2026-05")).hasSize(2);
    }

    @Test
    @DisplayName("findByReportYmAndDetectedTrue는 감지된 시도만 반환한다")
    void findsDetectedSidoStatusOnly() {
        reportRepository.save(report("2026-05", 748, 16));
        sidoStatusRepository.save(sidoStatus("2026-05", "강원", true, ReportGrade.경계));
        sidoStatusRepository.save(sidoStatus("2026-05", "제주", false, null));

        assertThat(sidoStatusRepository.findByReportYmAndDetectedTrue("2026-05"))
                .extracting(DroughtMonthlyReportSidoStatus::getSido)
                .containsExactly("강원");
        assertThat(sidoStatusRepository.findByReportYm("2026-05")).hasSize(2);
    }

    private static DroughtMonthlyReport report(String ym, int articleCount, int detectedSidoCount) {
        return DroughtMonthlyReport.builder()
                .reportYm(ym)
                .generatedAt(LocalDateTime.of(2026, 8, 30, 0, 0))
                .articleCount(articleCount)
                .detectedSidoCount(detectedSidoCount)
                .build();
    }

    private static DroughtMonthlyReportBucket bucket(String ym, String sido, String sigungu, String impactCode, ReportGrade grade) {
        return DroughtMonthlyReportBucket.builder()
                .reportYm(ym).sido(sido).sigungu(sigungu).impactCode(impactCode)
                .articleCount(1).grade(grade).relevanceFlag(false).continuityCount(1)
                .build();
    }

    private static DroughtMonthlyReportSidoStatus sidoStatus(String ym, String sido, boolean detected, ReportGrade maxGrade) {
        return DroughtMonthlyReportSidoStatus.builder()
                .reportYm(ym).sido(sido).detected(detected).maxGrade(maxGrade)
                .build();
    }

    @SpringBootConfiguration
    static class TestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :core-persistence:test --tests "re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtReportRepositoriesTest" --console=plain`
Expected: FAIL — 리포지토리 인터페이스가 없어 컴파일 실패.

- [ ] **Step 3: 3개 리포지토리 인터페이스 작성**

```java
package re.kr.icuh.drought.persistence.openapi.drought.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReport;

import java.util.Optional;

@Repository
public interface DroughtMonthlyReportRepository extends JpaRepository<DroughtMonthlyReport, String> {

    Optional<DroughtMonthlyReport> findTopByOrderByReportYmDesc();
}
```

```java
package re.kr.icuh.drought.persistence.openapi.drought.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucketId;

import java.util.List;

@Repository
public interface DroughtMonthlyReportBucketRepository
        extends JpaRepository<DroughtMonthlyReportBucket, DroughtMonthlyReportBucketId> {

    List<DroughtMonthlyReportBucket> findByReportYm(String reportYm);
}
```

```java
package re.kr.icuh.drought.persistence.openapi.drought.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatus;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatusId;

import java.util.List;

@Repository
public interface DroughtMonthlyReportSidoStatusRepository
        extends JpaRepository<DroughtMonthlyReportSidoStatus, DroughtMonthlyReportSidoStatusId> {

    List<DroughtMonthlyReportSidoStatus> findByReportYm(String reportYm);

    List<DroughtMonthlyReportSidoStatus> findByReportYmAndDetectedTrue(String reportYm);
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :core-persistence:test --console=plain`
Expected: PASS (core-persistence 전체 — 기존 `PersistenceSliceTest` 포함)

- [ ] **Step 5: 커밋**

```bash
git add core-persistence/src/main/java/re/kr/icuh/drought/persistence/openapi/drought/repository core-persistence/src/test/java/re/kr/icuh/drought/persistence/openapi/drought
git commit -m "feat(drought): 리포지토리 3종 + DataJpaTest 추가"
```

---

## Task 4: 응답 DTO + `DroughtReportService` (core-application)

**Files:**
- Create: `core-application/src/main/java/re/kr/icuh/drought/application/openapi/drought/response/ImpactBucketResponse.java`
- Create: `core-application/src/main/java/re/kr/icuh/drought/application/openapi/drought/response/RegionSectionResponse.java`
- Create: `core-application/src/main/java/re/kr/icuh/drought/application/openapi/drought/response/SidoStatusResponse.java`
- Create: `core-application/src/main/java/re/kr/icuh/drought/application/openapi/drought/response/DroughtReportListResponse.java`
- Create: `core-application/src/main/java/re/kr/icuh/drought/application/openapi/drought/response/DroughtReportDetailResponse.java`
- Create: `core-application/src/main/java/re/kr/icuh/drought/application/openapi/drought/service/DroughtReportService.java`
- Test: `core-application/src/test/java/re/kr/icuh/drought/application/openapi/drought/service/DroughtReportServiceTest.java`

**Interfaces:**
- Consumes: Task 2/3의 엔티티·리포지토리, `re.kr.icuh.drought.application.openapi.summary.response.SummaryAlertResponse`
  (이미 존재), `re.kr.icuh.drought.common.openapi.error.{CoreException, ErrorType}`(이미 존재).
- Produces: `DroughtReportService.getReports(Pageable): Page<DroughtReportListResponse>`,
  `DroughtReportService.getReportDetail(String reportYm): DroughtReportDetailResponse`(없으면
  `CoreException(ErrorType.DATA_NOT_FOUND)`), `DroughtReportService.getLatestDroughtAlerts(): List<SummaryAlertResponse>`.
  Task 5(컨트롤러)와 Task 6(SummaryService 배선)이 이 3개 메서드를 그대로 쓴다.

- [ ] **Step 1: 응답 DTO 4종 + 상세 응답 작성**

```java
package re.kr.icuh.drought.application.openapi.drought.response;

import re.kr.icuh.drought.domain.drought.DroughtImpactField;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;

import java.util.List;

public record ImpactBucketResponse(
        String impactCode,
        String impactName,
        String grade,
        boolean gradeFinalized,
        int articleCount,
        String representativeTitle,
        String representativeLink,
        List<String> keywords,
        boolean relevanceFlag,
        int continuityCount
) {
    public static ImpactBucketResponse of(DroughtMonthlyReportBucket bucket) {
        return new ImpactBucketResponse(
                bucket.getImpactCode(),
                DroughtImpactField.fromCode(bucket.getImpactCode()).displayName(),
                bucket.getGrade().name(),
                bucket.getGradeFinalizedAt() != null,
                bucket.getArticleCount(),
                bucket.getRepresentativeTitle(),
                bucket.getRepresentativeLink(),
                bucket.getKeywords(),
                bucket.isRelevanceFlag(),
                bucket.getContinuityCount()
        );
    }
}
```

```java
package re.kr.icuh.drought.application.openapi.drought.response;

import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;

import java.util.Comparator;
import java.util.List;

public record RegionSectionResponse(String sido, String sigungu, List<ImpactBucketResponse> impactFields) {
    public static RegionSectionResponse of(String sido, String sigungu, List<DroughtMonthlyReportBucket> buckets) {
        List<ImpactBucketResponse> fields = buckets.stream()
                .sorted(Comparator.comparing(DroughtMonthlyReportBucket::getImpactCode))
                .map(ImpactBucketResponse::of)
                .toList();
        return new RegionSectionResponse(sido, sigungu, fields);
    }
}
```

```java
package re.kr.icuh.drought.application.openapi.drought.response;

import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatus;

public record SidoStatusResponse(String sido, boolean detected, String maxGrade) {
    public static SidoStatusResponse of(DroughtMonthlyReportSidoStatus entity) {
        String grade = entity.getMaxGrade() == null ? null : entity.getMaxGrade().name();
        return new SidoStatusResponse(entity.getSido(), entity.isDetected(), grade);
    }
}
```

```java
package re.kr.icuh.drought.application.openapi.drought.response;

import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReport;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatus;

import java.util.Comparator;
import java.util.List;

public record DroughtReportListResponse(
        String reportYm,
        String headlineGrade,
        int detectedSidoCount,
        int articleCount,
        List<String> detectedSidoNames
) {
    public static DroughtReportListResponse of(DroughtMonthlyReport report, List<DroughtMonthlyReportSidoStatus> detected) {
        String headlineGrade = detected.stream()
                .map(DroughtMonthlyReportSidoStatus::getMaxGrade)
                .max(Comparator.naturalOrder())
                .map(Enum::name)
                .orElse(null);
        List<String> names = detected.stream().map(DroughtMonthlyReportSidoStatus::getSido).toList();
        return new DroughtReportListResponse(
                report.getReportYm(), headlineGrade, report.getDetectedSidoCount(), report.getArticleCount(), names);
    }
}
```

```java
package re.kr.icuh.drought.application.openapi.drought.response;

import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReport;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record DroughtReportDetailResponse(
        String reportYm,
        LocalDateTime generatedAt,
        int articleCount,
        int detectedSidoCount,
        List<SidoStatusResponse> nationwide,
        List<RegionSectionResponse> regions
) {
    public static DroughtReportDetailResponse of(
            DroughtMonthlyReport report,
            List<DroughtMonthlyReportSidoStatus> allSidoStatus,
            List<RegionSectionResponse> regions
    ) {
        List<SidoStatusResponse> nationwide = allSidoStatus.stream()
                .sorted(Comparator.comparing(DroughtMonthlyReportSidoStatus::getSido))
                .map(SidoStatusResponse::of)
                .toList();
        return new DroughtReportDetailResponse(
                report.getReportYm(), report.getGeneratedAt(), report.getArticleCount(),
                report.getDetectedSidoCount(), nationwide, regions);
    }
}
```

- [ ] **Step 2: 실패하는 `DroughtReportServiceTest` 작성**

```java
package re.kr.icuh.drought.application.openapi.drought.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import re.kr.icuh.drought.application.openapi.drought.response.DroughtReportDetailResponse;
import re.kr.icuh.drought.application.openapi.drought.response.DroughtReportListResponse;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryAlertResponse;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import re.kr.icuh.drought.domain.drought.ReportGrade;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReport;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatus;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportBucketRepository;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportRepository;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportSidoStatusRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DroughtReportServiceTest {

    @Mock
    private DroughtMonthlyReportRepository reportRepository;
    @Mock
    private DroughtMonthlyReportBucketRepository bucketRepository;
    @Mock
    private DroughtMonthlyReportSidoStatusRepository sidoStatusRepository;

    private DroughtReportService service;

    @BeforeEach
    void setUp() {
        service = new DroughtReportService(reportRepository, bucketRepository, sidoStatusRepository);
    }

    @Test
    @DisplayName("목록의 headlineGrade는 감지된 시도 중 최고 등급이다")
    void listComputesHeadlineGradeFromDetectedSido() {
        DroughtMonthlyReport report = report("2026-05", 748, 16);
        when(reportRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(report)));
        when(sidoStatusRepository.findByReportYmAndDetectedTrue("2026-05")).thenReturn(List.of(
                sidoStatus("2026-05", "강원", true, ReportGrade.경계),
                sidoStatus("2026-05", "제주", true, ReportGrade.관심)
        ));

        Page<DroughtReportListResponse> result = service.getReports(PageRequest.of(0, 10));

        DroughtReportListResponse first = result.getContent().get(0);
        assertThat(first.headlineGrade()).isEqualTo("경계");
        assertThat(first.detectedSidoNames()).containsExactlyInAnyOrder("강원", "제주");
    }

    @Test
    @DisplayName("존재하지 않는 reportYm 상세 조회는 DATA_NOT_FOUND를 던진다")
    void detailThrowsWhenReportMissing() {
        when(reportRepository.findById("1999-01")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReportDetail("1999-01"))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.DATA_NOT_FOUND);
    }

    @Test
    @DisplayName("상세 조회는 버킷을 (시도,시군구)로 묶어 지역 섹션을 만든다")
    void detailGroupsBucketsIntoRegions() {
        DroughtMonthlyReport report = report("2026-05", 748, 16);
        when(reportRepository.findById("2026-05")).thenReturn(Optional.of(report));
        when(sidoStatusRepository.findByReportYm("2026-05")).thenReturn(List.of(
                sidoStatus("2026-05", "강원", true, ReportGrade.경계)
        ));
        when(bucketRepository.findByReportYm("2026-05")).thenReturn(List.of(
                bucket("2026-05", "강원", "강릉", "A1", 12, ReportGrade.심각),
                bucket("2026-05", "강원", "강릉", "A3", 7, ReportGrade.경계)
        ));

        DroughtReportDetailResponse detail = service.getReportDetail("2026-05");

        assertThat(detail.regions()).hasSize(1);
        assertThat(detail.regions().get(0).sido()).isEqualTo("강원");
        assertThat(detail.regions().get(0).sigungu()).isEqualTo("강릉");
        assertThat(detail.regions().get(0).impactFields()).hasSize(2);
    }

    @Test
    @DisplayName("최신 리포트가 없으면 alerts는 빈 리스트다")
    void alertsEmptyWhenNoReportExists() {
        when(reportRepository.findTopByOrderByReportYmDesc()).thenReturn(Optional.empty());

        assertThat(service.getLatestDroughtAlerts()).isEmpty();
    }

    @Test
    @DisplayName("경계/심각 지역만, 등급 내림차순-기사수 내림차순으로 상위 3건만 alerts에 담는다")
    void alertsFilterSortAndLimit() {
        DroughtMonthlyReport report = report("2026-05", 748, 16);
        when(reportRepository.findTopByOrderByReportYmDesc()).thenReturn(Optional.of(report));
        when(bucketRepository.findByReportYm("2026-05")).thenReturn(List.of(
                bucket("2026-05", "강원", "강릉", "A1", 12, ReportGrade.심각),
                bucket("2026-05", "강원", "강릉", "A3", 7, ReportGrade.경계),
                bucket("2026-05", "경남", "합천", "A5", 5, ReportGrade.경계),
                bucket("2026-05", "전남", "고흥", "A4", 4, ReportGrade.경계),
                bucket("2026-05", "충북", "청주", "A2", 2, ReportGrade.경계),
                bucket("2026-05", "제주", "", "A8", 3, ReportGrade.관심)
        ));

        List<SummaryAlertResponse> alerts = service.getLatestDroughtAlerts();

        assertThat(alerts).hasSize(3);
        assertThat(alerts).extracting(SummaryAlertResponse::regionName)
                .containsExactly("강릉", "합천", "고흥");
        assertThat(alerts.get(0).regionCode()).isNull();
        assertThat(alerts.get(0).severity()).isEqualTo("danger");
        assertThat(alerts.get(0).value()).isEqualTo(19);
        assertThat(alerts.get(0).relatedReportCount()).isEqualTo(2);
    }

    private static DroughtMonthlyReport report(String ym, int articleCount, int detectedSidoCount) {
        return DroughtMonthlyReport.builder()
                .reportYm(ym)
                .generatedAt(LocalDateTime.of(2026, 8, 30, 15, 39))
                .articleCount(articleCount)
                .detectedSidoCount(detectedSidoCount)
                .build();
    }

    private static DroughtMonthlyReportSidoStatus sidoStatus(String ym, String sido, boolean detected, ReportGrade grade) {
        return DroughtMonthlyReportSidoStatus.builder()
                .reportYm(ym).sido(sido).detected(detected).maxGrade(grade)
                .build();
    }

    private static DroughtMonthlyReportBucket bucket(
            String ym, String sido, String sigungu, String impactCode, int articleCount, ReportGrade grade
    ) {
        return DroughtMonthlyReportBucket.builder()
                .reportYm(ym).sido(sido).sigungu(sigungu).impactCode(impactCode)
                .articleCount(articleCount).grade(grade)
                .representativeTitle("대표기사 " + sido + sigungu)
                .representativeLink("https://example.com")
                .keywords(List.of("가뭄"))
                .relevanceFlag(false).continuityCount(1)
                .build();
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :core-application:test --tests "re.kr.icuh.drought.application.openapi.drought.service.DroughtReportServiceTest" --console=plain`
Expected: FAIL — `DroughtReportService`가 없어 컴파일 실패.

- [ ] **Step 4: `DroughtReportService` 구현**

```java
package re.kr.icuh.drought.application.openapi.drought.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.application.openapi.drought.response.DroughtReportDetailResponse;
import re.kr.icuh.drought.application.openapi.drought.response.DroughtReportListResponse;
import re.kr.icuh.drought.application.openapi.drought.response.RegionSectionResponse;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryAlertResponse;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import re.kr.icuh.drought.domain.drought.DroughtImpactField;
import re.kr.icuh.drought.domain.drought.ReportGrade;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReport;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportBucketRepository;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportRepository;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportSidoStatusRepository;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DroughtReportService {

    private static final int SUMMARY_ALERT_LIMIT = 3;
    private static final Set<ReportGrade> SUMMARY_ALERT_GRADES = EnumSet.of(ReportGrade.경계, ReportGrade.심각);

    private final DroughtMonthlyReportRepository reportRepository;
    private final DroughtMonthlyReportBucketRepository bucketRepository;
    private final DroughtMonthlyReportSidoStatusRepository sidoStatusRepository;

    public DroughtReportService(
            DroughtMonthlyReportRepository reportRepository,
            DroughtMonthlyReportBucketRepository bucketRepository,
            DroughtMonthlyReportSidoStatusRepository sidoStatusRepository
    ) {
        this.reportRepository = reportRepository;
        this.bucketRepository = bucketRepository;
        this.sidoStatusRepository = sidoStatusRepository;
    }

    public Page<DroughtReportListResponse> getReports(Pageable pageable) {
        return reportRepository.findAll(pageable)
                .map(report -> DroughtReportListResponse.of(
                        report,
                        sidoStatusRepository.findByReportYmAndDetectedTrue(report.getReportYm())));
    }

    public DroughtReportDetailResponse getReportDetail(String reportYm) {
        DroughtMonthlyReport report = reportRepository.findById(reportYm)
                .orElseThrow(() -> new CoreException(ErrorType.DATA_NOT_FOUND));

        List<RegionSectionResponse> regions = groupByRegion(bucketRepository.findByReportYm(reportYm)).entrySet().stream()
                .sorted(Comparator.<Map.Entry<RegionKey, List<DroughtMonthlyReportBucket>>, String>comparing(e -> e.getKey().sido())
                        .thenComparing(e -> e.getKey().sigungu()))
                .map(e -> RegionSectionResponse.of(e.getKey().sido(), e.getKey().sigungu(), e.getValue()))
                .toList();

        return DroughtReportDetailResponse.of(report, sidoStatusRepository.findByReportYm(reportYm), regions);
    }

    public List<SummaryAlertResponse> getLatestDroughtAlerts() {
        Optional<DroughtMonthlyReport> latest = reportRepository.findTopByOrderByReportYmDesc();
        if (latest.isEmpty()) {
            return List.of();
        }
        DroughtMonthlyReport report = latest.get();

        return groupByRegion(bucketRepository.findByReportYm(report.getReportYm())).entrySet().stream()
                .map(e -> toRegionSummary(e.getKey(), e.getValue()))
                .filter(rs -> SUMMARY_ALERT_GRADES.contains(rs.maxGrade()))
                .sorted(Comparator.comparing(RegionSummary::maxGrade).reversed()
                        .thenComparing(Comparator.comparingInt(RegionSummary::totalArticleCount).reversed()))
                .limit(SUMMARY_ALERT_LIMIT)
                .map(rs -> toAlertResponse(report, rs))
                .toList();
    }

    private static Map<RegionKey, List<DroughtMonthlyReportBucket>> groupByRegion(List<DroughtMonthlyReportBucket> buckets) {
        return buckets.stream()
                .collect(Collectors.groupingBy(b -> new RegionKey(b.getSido(), b.getSigungu())));
    }

    private static RegionSummary toRegionSummary(RegionKey key, List<DroughtMonthlyReportBucket> buckets) {
        DroughtMonthlyReportBucket representative = buckets.stream()
                .max(Comparator.comparing(DroughtMonthlyReportBucket::getGrade)
                        .thenComparingInt(DroughtMonthlyReportBucket::getArticleCount))
                .orElseThrow();
        int totalArticleCount = buckets.stream().mapToInt(DroughtMonthlyReportBucket::getArticleCount).sum();
        return new RegionSummary(key, representative.getGrade(), totalArticleCount, representative, buckets.size());
    }

    private static SummaryAlertResponse toAlertResponse(DroughtMonthlyReport report, RegionSummary rs) {
        String sido = rs.key().sido();
        String sigungu = rs.key().sigungu();
        String regionName = sigungu.isEmpty() ? sido : sigungu;
        String id = "drought-" + report.getReportYm() + "-" + sido + "-" + sigungu;
        String impactName = DroughtImpactField.fromCode(rs.representative().getImpactCode()).displayName();
        String description = impactName + " 부문 관련 기사 " + rs.representative().getArticleCount() + "건 발행";

        return new SummaryAlertResponse(
                id,
                "drought-report",
                "drought-report",
                null,
                regionName,
                rs.representative().getRepresentativeTitle(),
                description,
                severityOf(rs.maxGrade()),
                scoreOf(rs.maxGrade()),
                rs.totalArticleCount(),
                "article_count",
                report.getGeneratedAt().toLocalDate().toString(),
                rs.bucketCount()
        );
    }

    private static String severityOf(ReportGrade grade) {
        return switch (grade) {
            case 관심 -> "info";
            case 주의, 경계 -> "warning";
            case 심각 -> "danger";
        };
    }

    private static int scoreOf(ReportGrade grade) {
        return switch (grade) {
            case 관심 -> 25;
            case 주의 -> 50;
            case 경계 -> 75;
            case 심각 -> 95;
        };
    }

    private record RegionKey(String sido, String sigungu) {
    }

    private record RegionSummary(
            RegionKey key, ReportGrade maxGrade, int totalArticleCount,
            DroughtMonthlyReportBucket representative, int bucketCount
    ) {
    }
}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew :core-application:test --console=plain`
Expected: PASS (core-application 전체)

- [ ] **Step 6: 커밋**

```bash
git add core-application/src/main/java/re/kr/icuh/drought/application/openapi/drought core-application/src/test/java/re/kr/icuh/drought/application/openapi/drought
git commit -m "feat(drought): 응답 DTO + DroughtReportService(목록/상세/summary 기여) 추가"
```

---

## Task 5: `DroughtReportController` (open-api)

**Files:**
- Create: `open-api/src/main/java/re/kr/icuh/drought/openapi/core/api/drought/DroughtReportController.java`
- Test: `open-api/src/test/java/re/kr/icuh/drought/openapi/core/api/drought/DroughtReportControllerTest.java`

**Interfaces:**
- Consumes: `DroughtReportService`(Task 4), `ApiResponse`(기존), `CoreException`/`ErrorType`(기존).
- Produces: `GET /api/v1/drought/reports`, `GET /api/v1/drought/reports/{reportYm}`.

- [ ] **Step 1: 실패하는 `@WebMvcTest` 작성**

`WildFireRiskIndexApiControllerTest`와 동일한 뼈대(`@WebMvcTest` + `@MockitoBean`)를 따른다.

```java
package re.kr.icuh.drought.openapi.core.api.drought;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import re.kr.icuh.drought.application.openapi.drought.response.DroughtReportDetailResponse;
import re.kr.icuh.drought.application.openapi.drought.service.DroughtReportService;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DroughtReportController.class)
class DroughtReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DroughtReportService droughtReportService;

    @Test
    @DisplayName("목록 조회는 200과 SUCCESS 래퍼로 응답한다")
    void returnsSuccessForList() throws Exception {
        when(droughtReportService.getReports(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/drought/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));
    }

    @Test
    @DisplayName("상세 조회는 200과 데이터를 응답한다")
    void returnsDetail() throws Exception {
        DroughtReportDetailResponse detail = new DroughtReportDetailResponse(
                "2026-05", LocalDateTime.of(2026, 8, 30, 15, 39), 748, 16, List.of(), List.of());
        when(droughtReportService.getReportDetail(eq("2026-05"))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/drought/reports/2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.reportYm").value("2026-05"))
                .andExpect(jsonPath("$.data.articleCount").value(748));
    }

    @Test
    @DisplayName("없는 reportYm 조회는 404를 응답한다")
    void returnsNotFoundForMissingReport() throws Exception {
        when(droughtReportService.getReportDetail(eq("1999-01")))
                .thenThrow(new CoreException(ErrorType.DATA_NOT_FOUND));

        mockMvc.perform(get("/api/v1/drought/reports/1999-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value("ERROR"));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :open-api:test --tests "re.kr.icuh.drought.openapi.core.api.drought.DroughtReportControllerTest" --console=plain`
Expected: FAIL — `DroughtReportController`가 없어 컴파일 실패.

- [ ] **Step 3: `DroughtReportController` 구현**

```java
package re.kr.icuh.drought.openapi.core.api.drought;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import re.kr.icuh.drought.application.openapi.drought.response.DroughtReportDetailResponse;
import re.kr.icuh.drought.application.openapi.drought.response.DroughtReportListResponse;
import re.kr.icuh.drought.application.openapi.drought.service.DroughtReportService;
import re.kr.icuh.drought.common.openapi.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/drought")
public class DroughtReportController {

    private final DroughtReportService droughtReportService;

    public DroughtReportController(DroughtReportService droughtReportService) {
        this.droughtReportService = droughtReportService;
    }

    @GetMapping("/reports")
    public ApiResponse<Page<DroughtReportListResponse>> getReports(
            @PageableDefault(size = 10, sort = "reportYm", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(droughtReportService.getReports(pageable));
    }

    @GetMapping("/reports/{reportYm}")
    public ApiResponse<DroughtReportDetailResponse> getReportDetail(@PathVariable String reportYm) {
        return ApiResponse.success(droughtReportService.getReportDetail(reportYm));
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :open-api:test --tests "re.kr.icuh.drought.openapi.core.api.drought.DroughtReportControllerTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add open-api/src/main/java/re/kr/icuh/drought/openapi/core/api/drought open-api/src/test/java/re/kr/icuh/drought/openapi/core/api/drought
git commit -m "feat(drought): 리포트 목록/상세 컨트롤러 추가"
```

---

## Task 6: `SummaryService`에 drought 알림 배선

**Files:**
- Modify: `core-application/src/main/java/re/kr/icuh/drought/application/openapi/summary/service/SummaryService.java`
- Create: `core-application/src/test/java/re/kr/icuh/drought/application/openapi/summary/service/SummaryServiceTest.java`

**Interfaces:**
- Consumes: `DroughtReportService.getLatestDroughtAlerts()`(Task 4).
- 기존 `SummaryController`/`SummaryControllerTest`는 `SummaryService`를 목으로 대체하므로 변경 없음
  (`SummaryController`가 `SummaryService`만 의존하지 `DroughtReportService`를 직접 알지 못한다).

- [ ] **Step 1: 실패하는 `SummaryServiceTest` 작성**

```java
package re.kr.icuh.drought.application.openapi.summary.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import re.kr.icuh.drought.application.openapi.drought.service.DroughtReportService;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryAlertResponse;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock
    private DroughtReportService droughtReportService;

    @Test
    @DisplayName("drought 알림을 summary의 alerts에 그대로 담고, kpis는 비워둔다")
    void includesDroughtAlertsInSummary() {
        SummaryAlertResponse alert = new SummaryAlertResponse(
                "drought-2026-05-강원-강릉", "drought-report", "drought-report",
                null, "강릉", "강릉 상수원 저수율 20%대 진입", "물 공급 부문 관련 기사 12건 발행",
                "danger", 95, 19, "article_count", "2026-08-30", 2);
        when(droughtReportService.getLatestDroughtAlerts()).thenReturn(List.of(alert));

        SummaryService summaryService = new SummaryService(droughtReportService);
        SummaryResponse summary = summaryService.getSummary();

        assertThat(summary.alerts()).containsExactly(alert);
        assertThat(summary.kpis()).isEmpty();
        assertThat(summary.generatedAt()).isNotBlank();
    }

    @Test
    @DisplayName("drought 알림이 없으면 alerts는 빈 리스트다")
    void emptyAlertsWhenNoneQualify() {
        when(droughtReportService.getLatestDroughtAlerts()).thenReturn(List.of());

        SummaryService summaryService = new SummaryService(droughtReportService);
        SummaryResponse summary = summaryService.getSummary();

        assertThat(summary.alerts()).isEmpty();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :core-application:test --tests "re.kr.icuh.drought.application.openapi.summary.service.SummaryServiceTest" --console=plain`
Expected: FAIL — `SummaryService`에 `DroughtReportService`를 받는 생성자가 없어 컴파일 실패.

- [ ] **Step 3: `SummaryService` 수정**

```java
package re.kr.icuh.drought.application.openapi.summary.service;

import org.springframework.stereotype.Service;
import re.kr.icuh.drought.application.openapi.drought.service.DroughtReportService;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryResponse;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SummaryService {

    private final DroughtReportService droughtReportService;

    public SummaryService(DroughtReportService droughtReportService) {
        this.droughtReportService = droughtReportService;
    }

    public SummaryResponse getSummary() {
        return new SummaryResponse(
                OffsetDateTime.now().toString(),
                droughtReportService.getLatestDroughtAlerts(),
                List.of()
        );
    }
}
```

- [ ] **Step 4: 전체 테스트로 통과 + 회귀 확인**

Run: `./gradlew :core-application:test :open-api:test --console=plain`
Expected: PASS — 새 `SummaryServiceTest`뿐 아니라 기존 `SummaryControllerTest`(open-api, `SummaryService`를
목으로 대체하므로 이번 변경과 무관하게 계속 통과해야 한다)도 함께 확인한다.

- [ ] **Step 5: 커밋**

```bash
git add core-application/src/main/java/re/kr/icuh/drought/application/openapi/summary/service/SummaryService.java core-application/src/test/java/re/kr/icuh/drought/application/openapi/summary/service/SummaryServiceTest.java
git commit -m "feat(drought): SummaryService에 drought alerts 배선"
```

---

## 마무리

- [ ] **저장소 전체 빌드 확인**

Run: `./gradlew clean build --console=plain`
Expected: BUILD SUCCESSFUL (전 모듈 컴파일 + 테스트)

- [ ] **운영 RDS DDL 적용 확인** — 스펙 §3/§10. 로컬 dev DB에는 2026-08-30에 적용해 실측까지
  마쳤지만(2026-05 리포트: 기사 748건·버킷 212개·16/17 시도 감지), **운영 RDS에는 아직 적용되지
  않았다.** 이 플랜의 코드를 운영에 배포하기 전에 `sql/schema.sql`의 4개 신규 테이블 DDL을 운영
  RDS에서 직접 실행해야 한다(이 저장소는 `ddl-auto: none`이라 코드가 테이블을 만들어주지 않는다).
