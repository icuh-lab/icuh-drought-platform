package re.kr.icuh.drought.openapi.core.api.freshfood;

import re.kr.icuh.drought.application.openapi.freshfood.request.FreshFoodIndexRequest;
import re.kr.icuh.drought.application.openapi.freshfood.response.FreshFruitIndexResponse;
import re.kr.icuh.drought.application.openapi.freshfood.response.FreshVegetableIndexResponse;
import re.kr.icuh.drought.application.openapi.freshfood.service.FreshFoodService;
import re.kr.icuh.drought.common.openapi.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/freshfood")
public class FreshFoodApiController {

    private final FreshFoodService freshFoodService;

    public FreshFoodApiController(FreshFoodService freshFoodService) {
        this.freshFoodService = freshFoodService;
    }

    @GetMapping("/fresh-vegetable")
    public ApiResponse<FreshVegetableIndexResponse> getFreshVegetableIndex(@Valid @ModelAttribute FreshFoodIndexRequest freshFoodIndexRequest) {
        return ApiResponse.success(freshFoodService.getFreshVegetableIndex(freshFoodIndexRequest));
    }

    @GetMapping("/fresh-fruit")
    public ApiResponse<FreshFruitIndexResponse> getFreshFruitIndex(@Valid @ModelAttribute FreshFoodIndexRequest freshFoodIndexRequest) {
        return ApiResponse.success(freshFoodService.getFreshFruitIndex(freshFoodIndexRequest));
    }
}
