package re.kr.icuh.drought.application.openapi.predictionvintage.request;

import jakarta.validation.constraints.NotBlank;

public record PredictionVintageRequest(
        @NotBlank(message = "지역명은 필수 값입니다.")
        String location
) {
}
