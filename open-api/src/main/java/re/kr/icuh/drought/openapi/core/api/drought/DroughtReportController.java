package re.kr.icuh.drought.openapi.core.api.drought;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import re.kr.icuh.drought.application.openapi.drought.response.DroughtReportDetailResponse;
import re.kr.icuh.drought.application.openapi.drought.response.DroughtReportListResponse;
import re.kr.icuh.drought.application.openapi.drought.service.DroughtReportService;
import re.kr.icuh.drought.common.openapi.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/drought")
public class DroughtReportController {

    private final DroughtReportService droughtReportService;

    public DroughtReportController(DroughtReportService droughtReportService) {
        this.droughtReportService = droughtReportService;
    }

    @GetMapping("/reports")
    public ApiResponse<Page<DroughtReportListResponse>> getReports(
            @PageableDefault(size = 10, sort = "reportYm", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(droughtReportService.getReports(pageable));
    }

    @GetMapping("/reports/{reportYm}")
    public ApiResponse<DroughtReportDetailResponse> getReportDetail(@PathVariable String reportYm) {
        return ApiResponse.success(droughtReportService.getReportDetail(reportYm));
    }
}
