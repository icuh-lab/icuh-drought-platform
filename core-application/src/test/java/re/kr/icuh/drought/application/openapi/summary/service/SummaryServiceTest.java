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

    @Test
    @DisplayName("drought 알림 조회가 실패해도 summary는 alerts 빈 리스트로 정상 응답한다")
    void fallsBackToEmptyAlertsWhenDroughtReportServiceFails() {
        when(droughtReportService.getLatestDroughtAlerts()).thenThrow(new RuntimeException("drought table missing"));

        SummaryService summaryService = new SummaryService(droughtReportService);
        SummaryResponse summary = summaryService.getSummary();

        assertThat(summary.alerts()).isEmpty();
        assertThat(summary.kpis()).isEqualTo(List.of());
    }
}
