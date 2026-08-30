package re.kr.icuh.drought.openapi.core.api.hydropower;

import re.kr.icuh.drought.application.openapi.hydropower.request.HydroPowerDamRequest;
import re.kr.icuh.drought.application.openapi.hydropower.request.HydroPowerRequest;
import re.kr.icuh.drought.application.openapi.hydropower.request.HydroPowerYearlyRequest;
import re.kr.icuh.drought.application.openapi.hydropower.response.comparison.DamMonthlyComparisonResponse;
import re.kr.icuh.drought.application.openapi.hydropower.response.generation.DamMonthlyGenerationResponse;
import re.kr.icuh.drought.application.openapi.hydropower.response.prediction.MonthlyDamPredictionHistoryResponse;
import re.kr.icuh.drought.application.openapi.hydropower.response.prediction.MonthlyDamPredictionResponse;
import re.kr.icuh.drought.application.openapi.hydropower.response.reservoir.DamMonthlyReservoirStatusResponse;
import re.kr.icuh.drought.application.openapi.hydropower.service.HydroPowerService;
import re.kr.icuh.drought.common.openapi.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hydropower")
public class HydroPowerApiController {

    private final HydroPowerService hydroPowerService;

    public HydroPowerApiController(HydroPowerService hydroPowerService) {
        this.hydroPowerService = hydroPowerService;
    }

    @GetMapping("/monthly-predict")
    public ApiResponse<MonthlyDamPredictionResponse> getMonthlyPredictions(@Valid @ModelAttribute HydroPowerRequest hydroPowerRequestDto) {

        MonthlyDamPredictionResponse monthlyDamPredictionDto = hydroPowerService.getMonthlyPredictions(hydroPowerRequestDto);

        return ApiResponse.success(monthlyDamPredictionDto);
    }

    @GetMapping("/monthly-comparison")
    public ApiResponse<DamMonthlyComparisonResponse> getMonthlyComparison(@Valid @ModelAttribute HydroPowerRequest hydroPowerRequestDto) {

        DamMonthlyComparisonResponse damMonthlyComparisonDto = hydroPowerService.getMonthlyComparison(hydroPowerRequestDto);

        return ApiResponse.success(damMonthlyComparisonDto);
    }

    @GetMapping("/monthly-generation")
    public ApiResponse<DamMonthlyGenerationResponse> getMonthlyGeneration(@Valid @ModelAttribute HydroPowerYearlyRequest hydroPowerYearlyRequestDto) {

        DamMonthlyGenerationResponse damMonthlyGenerationDto = hydroPowerService.getMonthlyGeneration(hydroPowerYearlyRequestDto);

        return ApiResponse.success(damMonthlyGenerationDto);
    }

    @GetMapping("/monthly-reservoir")
    public ApiResponse<DamMonthlyReservoirStatusResponse> getMonthlyReservoirStatus(@Valid @ModelAttribute HydroPowerYearlyRequest hydroPowerYearlyRequestDto) {

        DamMonthlyReservoirStatusResponse damMonthlyReservoirStatusDto = hydroPowerService.getMonthlyReservoirStatus(hydroPowerYearlyRequestDto);

        return ApiResponse.success(damMonthlyReservoirStatusDto);
    }

    @GetMapping("/monthly-predict-history")
    public ApiResponse<MonthlyDamPredictionHistoryResponse> getMonthlyPredictionHistory(@Valid @ModelAttribute HydroPowerDamRequest hydroPowerDamRequestDto) {

        MonthlyDamPredictionHistoryResponse monthlyDamPredictionHistoryDto = hydroPowerService.getMonthlyPredictionHistory(hydroPowerDamRequestDto);

        return ApiResponse.success(monthlyDamPredictionHistoryDto);
    }
}
