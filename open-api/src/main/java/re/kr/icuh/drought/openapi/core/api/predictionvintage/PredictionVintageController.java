package re.kr.icuh.drought.openapi.core.api.predictionvintage;

import re.kr.icuh.drought.application.openapi.predictionvintage.request.PredictionVintageRequest;
import re.kr.icuh.drought.application.openapi.predictionvintage.response.PredictionVintageResponse;
import re.kr.icuh.drought.application.openapi.predictionvintage.service.PredictionVintageService;
import re.kr.icuh.drought.common.openapi.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agrimarket")
public class PredictionVintageController {

    private final PredictionVintageService predictionVintageService;

    public PredictionVintageController(PredictionVintageService predictionVintageService) {
        this.predictionVintageService = predictionVintageService;
    }

    @GetMapping("/prediction-vintage")
    public ApiResponse<PredictionVintageResponse> getPredictionVintage(@Valid @ModelAttribute PredictionVintageRequest predictionVintageRequestDto) {

        PredictionVintageResponse predictionVintageResponseDto = predictionVintageService.getPredictionVintage(predictionVintageRequestDto);

        return ApiResponse.success(predictionVintageResponseDto);
    }
}
