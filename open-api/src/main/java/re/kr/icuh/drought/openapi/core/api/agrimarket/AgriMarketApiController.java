package re.kr.icuh.drought.openapi.core.api.agrimarket;

import re.kr.icuh.drought.application.openapi.agrimarket.request.AgriMarketRequest;
import re.kr.icuh.drought.application.openapi.agrimarket.response.calendar.DailyPricePredictionResponse;
import re.kr.icuh.drought.application.openapi.agrimarket.response.prediction.MonthlyMarketPredictionResponse;
import re.kr.icuh.drought.application.openapi.agrimarket.response.trend.DailyMarketTrendResponse;
import re.kr.icuh.drought.application.openapi.agrimarket.service.AgriMarketService;
import re.kr.icuh.drought.common.openapi.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agrimarket")
public class AgriMarketApiController {

    private final AgriMarketService agriMarketService;

    public AgriMarketApiController(AgriMarketService agriMarketService) {
        this.agriMarketService = agriMarketService;
    }

    @GetMapping("/market-price")
    public ApiResponse<MonthlyMarketPredictionResponse> getAgriMarketPricePredict(@Valid @ModelAttribute AgriMarketRequest agriMarketRequestDto) {

        MonthlyMarketPredictionResponse monthlyMarketPredictionDto = agriMarketService.getAgriMarketPricePredict(agriMarketRequestDto);

        return ApiResponse.success(monthlyMarketPredictionDto);
    }

    @GetMapping("/daily-price")
    public ApiResponse<DailyPricePredictionResponse> getDailyPricePrediction(@Valid @ModelAttribute AgriMarketRequest agriMarketRequestDto) {

        DailyPricePredictionResponse dailyPricePredictionDto = agriMarketService.getDailyPricePrediction(agriMarketRequestDto);

        return ApiResponse.success(dailyPricePredictionDto);
    }

    @GetMapping("/daily-market")
    public ApiResponse<DailyMarketTrendResponse> getDailyMarketTrend(@Valid @ModelAttribute AgriMarketRequest agriMarketRequestDto) {

        DailyMarketTrendResponse dailyMarketTrendDto = agriMarketService.getDailyMarketTrend(agriMarketRequestDto);

        return ApiResponse.success(dailyMarketTrendDto);
    }
}
