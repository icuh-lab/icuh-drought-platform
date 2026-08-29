package re.kr.icuh.drought.persistence.openapi.hydropower.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyGeneration;
import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyReservoirStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code HydroPowerRepository}의 {@code damMonthlyGeneration}/{@code damMonthlyReservoirStatus}가
 * 실제로 연-월 오름차순으로 정렬되어 반환되는지를 증명하는 슬라이스 테스트.
 * <p>
 * {@code month} 컬럼은 운영 DB에서 zero-padding 없는 문자열('1'..'12')로 저장되므로, 저장 순서를
 * 일부러 뒤섞어(10 -> 2 -> 1 -> 12) 저장한 뒤 리포지토리 조회 결과가 숫자 기준 오름차순(1, 2, 10, 12)으로
 * 나오는지 확인한다. {@code HydroPowerServiceTest}의 mock 기반 테스트는 서비스가 리포지토리 반환 순서를
 * 그대로 보존하는지만 증명할 뿐 JPQL의 {@code ORDER BY}를 검증하지 못하므로, 이 테스트가 실제 정렬 보장의
 * 회귀 가드 역할을 한다.
 * <p>
 * H2 2.x부터 {@code MONTH}/{@code YEAR}가 예약어가 되어 기본 임베디드 DB URL로는
 * {@code dam_monthly_generation}/{@code dam_monthly_reservoir_status}의 {@code month}/{@code year}
 * 컬럼을 포함한 {@code CREATE TABLE}이 실패한다(운영 MySQL에서는 이 이름들이 예약어가 아니라 문제없이
 * 동작하므로 엔티티의 {@code @Column} 매핑은 그대로 둔다 — 이 테스트에서만 H2 커넥션 속성
 * {@code NON_KEYWORDS}로 두 이름을 비예약어로 취급하도록 우회한다).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hydropower_repository_test;NON_KEYWORDS=MONTH,YEAR;DB_CLOSE_DELAY=-1"
})
@EntityScan(basePackages = "re.kr.icuh.drought.persistence")
@EnableJpaRepositories(basePackages = "re.kr.icuh.drought.persistence")
class HydroPowerRepositoryTest {

    @Autowired
    private HydroPowerRepository hydroPowerRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("월별 발전량은 저장 순서와 무관하게 연-월 오름차순으로 반환된다")
    void damMonthlyGenerationIsOrderedByMonthAscending() {
        persistGeneration("2026", "10", "소양강댐");
        persistGeneration("2026", "2", "소양강댐");
        persistGeneration("2026", "1", "소양강댐");
        persistGeneration("2026", "12", "소양강댐");
        entityManager.flush();
        entityManager.clear();

        List<DamMonthlyGeneration> result =
                hydroPowerRepository.damMonthlyGeneration("2026", "소양강댐");

        assertThat(result)
                .extracting(DamMonthlyGeneration::getMonth)
                .containsExactly("1", "2", "10", "12");
    }

    @Test
    @DisplayName("월별 저수량도 저장 순서와 무관하게 연-월 오름차순으로 반환된다")
    void damMonthlyReservoirStatusIsOrderedByMonthAscending() {
        persistReservoirStatus("2026", "10", "충주댐");
        persistReservoirStatus("2026", "2", "충주댐");
        persistReservoirStatus("2026", "1", "충주댐");
        persistReservoirStatus("2026", "12", "충주댐");
        entityManager.flush();
        entityManager.clear();

        List<DamMonthlyReservoirStatus> result =
                hydroPowerRepository.damMonthlyReservoirStatus("2026", "충주댐");

        assertThat(result)
                .extracting(DamMonthlyReservoirStatus::getMonth)
                .containsExactly("1", "2", "10", "12");
    }

    private void persistGeneration(String year, String month, String damName) {
        DamMonthlyGeneration entity = new DamMonthlyGeneration();
        ReflectionTestUtils.setField(entity, "year", year);
        ReflectionTestUtils.setField(entity, "month", month);
        ReflectionTestUtils.setField(entity, "damName", damName);
        entityManager.persist(entity);
    }

    private void persistReservoirStatus(String year, String month, String damName) {
        DamMonthlyReservoirStatus entity = new DamMonthlyReservoirStatus();
        ReflectionTestUtils.setField(entity, "year", year);
        ReflectionTestUtils.setField(entity, "month", month);
        ReflectionTestUtils.setField(entity, "damName", damName);
        entityManager.persist(entity);
    }

    @SpringBootConfiguration
    static class TestApplication {

        /**
         * {@code @DataJpaTest}는 Jackson을 자동 구성하지 않는다. {@code EntityScan}이
         * {@code re.kr.icuh.drought.persistence} 전체를 스캔하므로 Article의 JSON 컨버터도 함께
         * 로드되는데, 그 컨버터가 {@code ObjectMapper}를 생성자 주입받아 빈이 없으면 컨텍스트 로딩부터
         * 실패한다 (PersistenceSliceTest와 동일한 이유).
         */
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
