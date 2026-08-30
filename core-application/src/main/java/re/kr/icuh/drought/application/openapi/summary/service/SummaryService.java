package re.kr.icuh.drought.application.openapi.summary.service;

import org.springframework.stereotype.Service;
import re.kr.icuh.drought.application.openapi.drought.service.DroughtReportService;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryResponse;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SummaryService {

    private final DroughtReportService droughtReportService;

    public SummaryService(DroughtReportService droughtReportService) {
        this.droughtReportService = droughtReportService;
    }

    public SummaryResponse getSummary() {
        return new SummaryResponse(
                OffsetDateTime.now().toString(),
                droughtReportService.getLatestDroughtAlerts(),
                List.of()
        );
    }
}
