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
