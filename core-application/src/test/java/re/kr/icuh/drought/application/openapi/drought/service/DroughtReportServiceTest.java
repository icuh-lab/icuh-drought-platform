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
import re.kr.icuh.drought.application.openapi.drought.response.ImpactBucketResponse;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryAlertResponse;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import re.kr.icuh.drought.domain.drought.ReportGrade;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReport;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatus;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtReportGradeBreak;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportBucketRepository;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportRepository;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportSidoStatusRepository;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtReportGradeBreakRepository;

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
    @Mock
    private DroughtReportGradeBreakRepository gradeBreakRepository;

    private DroughtReportService service;

    @BeforeEach
    void setUp() {
        service = new DroughtReportService(reportRepository, bucketRepository, sidoStatusRepository, gradeBreakRepository);
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
    @DisplayName("재보정 버전이 하나도 없으면 확정된 버킷도 gradeLowerBound/nextGradeLowerBound는 null이다")
    void detailGradeLowerBoundNullWhenNoBreaksExist() {
        DroughtMonthlyReport report = report("2026-05", 748, 16);
        when(reportRepository.findById("2026-05")).thenReturn(Optional.of(report));
        when(sidoStatusRepository.findByReportYm("2026-05")).thenReturn(List.of());
        when(bucketRepository.findByReportYm("2026-05")).thenReturn(List.of(
                finalizedBucket("2026-05", "강원", "강릉", "A1", 12, ReportGrade.심각)
        ));
        when(gradeBreakRepository.findMaxVersion()).thenReturn(Optional.empty());

        ImpactBucketResponse field = service.getReportDetail("2026-05").regions().get(0).impactFields().get(0);

        assertThat(field.gradeLowerBound()).isNull();
        assertThat(field.nextGradeLowerBound()).isNull();
    }

    @Test
    @DisplayName("확정된 버킷은 최신 버전의 등급 기준선을 채운다")
    void detailFillsGradeLowerBoundFromLatestBreaksVersion() {
        DroughtMonthlyReport report = report("2026-05", 748, 16);
        when(reportRepository.findById("2026-05")).thenReturn(Optional.of(report));
        when(sidoStatusRepository.findByReportYm("2026-05")).thenReturn(List.of());
        when(bucketRepository.findByReportYm("2026-05")).thenReturn(List.of(
                finalizedBucket("2026-05", "강원", "강릉", "A1", 12, ReportGrade.경계)
        ));
        when(gradeBreakRepository.findMaxVersion()).thenReturn(Optional.of(2));
        when(gradeBreakRepository.findByVersion(2)).thenReturn(List.of(
                gradeBreak(2, "A1", ReportGrade.경계, 8.0),
                gradeBreak(2, "A1", ReportGrade.심각, 15.0)
        ));

        ImpactBucketResponse field = service.getReportDetail("2026-05").regions().get(0).impactFields().get(0);

        assertThat(field.gradeLowerBound()).isEqualTo(8.0);
        assertThat(field.nextGradeLowerBound()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("확정 안 된 버킷은 기준선이 있어도 gradeLowerBound/nextGradeLowerBound가 null이다")
    void detailGradeLowerBoundNullWhenBucketNotFinalized() {
        DroughtMonthlyReport report = report("2026-05", 748, 16);
        when(reportRepository.findById("2026-05")).thenReturn(Optional.of(report));
        when(sidoStatusRepository.findByReportYm("2026-05")).thenReturn(List.of());
        when(bucketRepository.findByReportYm("2026-05")).thenReturn(List.of(
                bucket("2026-05", "강원", "강릉", "A1", 12, ReportGrade.관심)
        ));
        when(gradeBreakRepository.findMaxVersion()).thenReturn(Optional.of(2));
        when(gradeBreakRepository.findByVersion(2)).thenReturn(List.of(
                gradeBreak(2, "A1", ReportGrade.관심, 1.0),
                gradeBreak(2, "A1", ReportGrade.주의, 5.0)
        ));

        ImpactBucketResponse field = service.getReportDetail("2026-05").regions().get(0).impactFields().get(0);

        assertThat(field.gradeLowerBound()).isNull();
        assertThat(field.nextGradeLowerBound()).isNull();
    }

    @Test
    @DisplayName("확정된 최고 등급(심각)은 자기 기준선은 채우고 nextGradeLowerBound는 null이다")
    void detailNextGradeLowerBoundNullAtTopGradeWithRealBreaks() {
        DroughtMonthlyReport report = report("2026-05", 748, 16);
        when(reportRepository.findById("2026-05")).thenReturn(Optional.of(report));
        when(sidoStatusRepository.findByReportYm("2026-05")).thenReturn(List.of());
        when(bucketRepository.findByReportYm("2026-05")).thenReturn(List.of(
                finalizedBucket("2026-05", "강원", "강릉", "A1", 20, ReportGrade.심각)
        ));
        when(gradeBreakRepository.findMaxVersion()).thenReturn(Optional.of(2));
        when(gradeBreakRepository.findByVersion(2)).thenReturn(List.of(
                gradeBreak(2, "A1", ReportGrade.심각, 15.0)
        ));

        ImpactBucketResponse field = service.getReportDetail("2026-05").regions().get(0).impactFields().get(0);

        assertThat(field.gradeLowerBound()).isEqualTo(15.0);
        assertThat(field.nextGradeLowerBound()).isNull();
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

    @Test
    @DisplayName("최신 리포트의 모든 버킷이 관심/주의 등급이면 alerts는 빈 리스트다")
    void alertsEmptyWhenNoBucketReachesThreshold() {
        DroughtMonthlyReport report = report("2026-05", 748, 16);
        when(reportRepository.findTopByOrderByReportYmDesc()).thenReturn(Optional.of(report));
        when(bucketRepository.findByReportYm("2026-05")).thenReturn(List.of(
                bucket("2026-05", "강원", "강릉", "A1", 12, ReportGrade.관심),
                bucket("2026-05", "경남", "합천", "A5", 5, ReportGrade.주의)
        ));

        assertThat(service.getLatestDroughtAlerts()).isEmpty();
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

    private static DroughtMonthlyReportBucket finalizedBucket(
            String ym, String sido, String sigungu, String impactCode, int articleCount, ReportGrade grade
    ) {
        return DroughtMonthlyReportBucket.builder()
                .reportYm(ym).sido(sido).sigungu(sigungu).impactCode(impactCode)
                .articleCount(articleCount).grade(grade)
                .gradeFinalizedAt(LocalDateTime.of(2026, 9, 30, 0, 0))
                .representativeTitle("대표기사 " + sido + sigungu)
                .representativeLink("https://example.com")
                .keywords(List.of("가뭄"))
                .relevanceFlag(false).continuityCount(1)
                .build();
    }

    private static DroughtReportGradeBreak gradeBreak(int version, String impactCode, ReportGrade grade, double lowerBound) {
        return DroughtReportGradeBreak.builder()
                .version(version).impactCode(impactCode).grade(grade)
                .lowerBound(lowerBound).computedAt(LocalDateTime.of(2026, 8, 30, 0, 0))
                .build();
    }
}
