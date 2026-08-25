package re.kr.icuh.drought.openapi.core.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryResponse;
import re.kr.icuh.drought.application.openapi.summary.service.SummaryService;
import re.kr.icuh.drought.common.openapi.response.ApiResponse;

@RestController
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/v1/summary")
    public ApiResponse<SummaryResponse> getSummary() {
        return ApiResponse.success(summaryService.getSummary());
    }
}
