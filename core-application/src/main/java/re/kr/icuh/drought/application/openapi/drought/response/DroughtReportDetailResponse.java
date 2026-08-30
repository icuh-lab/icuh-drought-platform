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
