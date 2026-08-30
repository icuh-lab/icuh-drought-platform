package re.kr.icuh.drought.application.openapi.summary.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import re.kr.icuh.drought.application.openapi.drought.service.DroughtReportService;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryAlertResponse;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryResponse;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
public class SummaryService {

    private final DroughtReportService droughtReportService;

    public SummaryService(DroughtReportService droughtReportService) {
        this.droughtReportService = droughtReportService;
    }

    public SummaryResponse getSummary() {
        return new SummaryResponse(
                OffsetDateTime.now().toString(),
                getLatestDroughtAlertsSafely(),
                List.of()
        );
    }

    /**
     * drought 테이블이 아직 배포되지 않은 환경에서도 기존 /v1/summary 계약(200 {alerts: []})을
     * 지키기 위해 조회 실패를 흡수한다. 빈 alerts는 정상 상태이지 버그가 아니다(spec §6.3).
     */
    private List<SummaryAlertResponse> getLatestDroughtAlertsSafely() {
        try {
            return droughtReportService.getLatestDroughtAlerts();
        } catch (RuntimeException e) {
            log.warn("drought alerts 조회 실패, 빈 리스트로 대체합니다: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
