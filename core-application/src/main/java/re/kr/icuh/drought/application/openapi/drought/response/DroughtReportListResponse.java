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
