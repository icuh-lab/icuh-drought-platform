package re.kr.icuh.drought.application.openapi.hydropower.response.prediction;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyPrediction;
import lombok.Builder;

public record PredictedPowerGenerationResponse(
    Integer predictedPowerGenerationLowerBound,
    Integer predictedPowerGenerationUpperBound
) {
    @Builder
    public PredictedPowerGenerationResponse {}

    public static PredictedPowerGenerationResponse of(DamMonthlyPrediction prediction) {
        return PredictedPowerGenerationResponse.builder()
                .predictedPowerGenerationLowerBound(prediction.getPredictedPowerGenerationLowerBound())
                .predictedPowerGenerationUpperBound(prediction.getPredictedPowerGenerationUpperBound())
                .build();
    }
}
