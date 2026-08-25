package re.kr.icuh.drought.application.openapi.summary.response;

import java.time.OffsetDateTime;
import java.util.List;

public record SummaryResponse(
        String generatedAt,
        List<SummaryAlertResponse> alerts,
        List<SummaryKpiResponse> kpis
) {
    public static SummaryResponse empty() {
        return new SummaryResponse(OffsetDateTime.now().toString(), List.of(), List.of());
    }
}
