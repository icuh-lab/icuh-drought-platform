package re.kr.icuh.drought.application.openapi.summary.response;

public record SummaryKpiResponse(
        String dataset,
        String regionCode,
        String regionName,
        String name,
        Number value,
        String unit,
        Number changeRate,
        String severity,
        String observedAt
) {
}
