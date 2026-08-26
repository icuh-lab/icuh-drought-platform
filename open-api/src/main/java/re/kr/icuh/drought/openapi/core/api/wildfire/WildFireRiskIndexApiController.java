package re.kr.icuh.drought.openapi.core.api.wildfire;

import re.kr.icuh.drought.application.openapi.wildfire.request.WildFireForecastRequest;
import re.kr.icuh.drought.application.openapi.wildfire.request.WildFireRiskIndexRequest;
import re.kr.icuh.drought.application.openapi.wildfire.response.ForecastResponse;
import re.kr.icuh.drought.application.openapi.wildfire.response.NewsArticleResponse;
import re.kr.icuh.drought.application.openapi.wildfire.service.WildFireRiskIndexService;
import re.kr.icuh.drought.common.openapi.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wild-fire-risk")
public class WildFireRiskIndexApiController {

    private final WildFireRiskIndexService wildFireRiskIndexService;

    public WildFireRiskIndexApiController(WildFireRiskIndexService wildFireRiskIndexService) {
        this.wildFireRiskIndexService = wildFireRiskIndexService;
    }

    @GetMapping("/forecast")
    public ApiResponse<List<ForecastResponse>> getForecast(@Valid @ModelAttribute WildFireForecastRequest wildFireForecastRequest) {
        return ApiResponse.success(wildFireRiskIndexService.getForeCast(wildFireForecastRequest));
    }

    @GetMapping("/news-article")
    public ApiResponse<List<NewsArticleResponse>> getNewsArticle(@Valid @ModelAttribute WildFireRiskIndexRequest wildFireRiskIndexRequest) {
        return ApiResponse.success(wildFireRiskIndexService.getNewsArticle(wildFireRiskIndexRequest));
    }

}
