package re.kr.icuh.drought.application.openapi.summary.response;

public record SummaryAlertResponse(
        String id,
        String category,
        String dataset,
        String regionCode,
        String regionName,
        String title,
        String description,
        String severity,
        Number score,
        Number value,
        String unit,
        String observedAt,
        int relatedReportCount
) {
}
