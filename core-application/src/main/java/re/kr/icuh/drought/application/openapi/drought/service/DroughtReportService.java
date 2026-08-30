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
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtReportGradeBreak;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportBucketRepository;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportRepository;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtMonthlyReportSidoStatusRepository;
import re.kr.icuh.drought.persistence.openapi.drought.repository.DroughtReportGradeBreakRepository;

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
    private final DroughtReportGradeBreakRepository gradeBreakRepository;

    public DroughtReportService(
            DroughtMonthlyReportRepository reportRepository,
            DroughtMonthlyReportBucketRepository bucketRepository,
            DroughtMonthlyReportSidoStatusRepository sidoStatusRepository,
            DroughtReportGradeBreakRepository gradeBreakRepository
    ) {
        this.reportRepository = reportRepository;
        this.bucketRepository = bucketRepository;
        this.sidoStatusRepository = sidoStatusRepository;
        this.gradeBreakRepository = gradeBreakRepository;
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

        Map<String, Map<ReportGrade, Double>> gradeBreaks = loadLatestGradeBreaks();

        List<RegionSectionResponse> regions = groupByRegion(bucketRepository.findByReportYm(reportYm)).entrySet().stream()
                .sorted(Comparator.<Map.Entry<RegionKey, List<DroughtMonthlyReportBucket>>, String>comparing(e -> e.getKey().sido())
                        .thenComparing(e -> e.getKey().sigungu()))
                .map(e -> RegionSectionResponse.of(e.getKey().sido(), e.getKey().sigungu(), e.getValue(), gradeBreaks))
                .toList();

        return DroughtReportDetailResponse.of(report, sidoStatusRepository.findByReportYm(reportYm), regions);
    }

    /**
     * 가장 최근 재보정 버전의 등급별 하한값을 영향분야별로 묶는다. 재보정이 한 번도 안 됐으면 빈 맵
     * (모든 버킷의 gradeLowerBound/nextGradeLowerBound가 null로 내려간다 — 버그 아님, ImpactBucketResponse 참고).
     */
    private Map<String, Map<ReportGrade, Double>> loadLatestGradeBreaks() {
        return gradeBreakRepository.findMaxVersion()
                .map(gradeBreakRepository::findByVersion)
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.groupingBy(
                        DroughtReportGradeBreak::getImpactCode,
                        Collectors.toMap(DroughtReportGradeBreak::getGrade, DroughtReportGradeBreak::getLowerBound)));
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
