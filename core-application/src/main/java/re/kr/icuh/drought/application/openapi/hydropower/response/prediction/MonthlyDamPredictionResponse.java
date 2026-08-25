package re.kr.icuh.drought.application.openapi.hydropower.response.prediction;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyPrediction;
import lombok.Builder;

public record MonthlyDamPredictionResponse(
    String damName,
    String damCode,
    PredictedPowerGenerationResponse predictedPowerGenerationDto,
    PredictedWaterStorageResponse predictedWaterStorageDto
) {
    @Builder
    public MonthlyDamPredictionResponse {}

    public static MonthlyDamPredictionResponse of(DamMonthlyPrediction prediction) {
        return MonthlyDamPredictionResponse.builder()
                .damName(prediction.getDamName())
                .damCode(prediction.getDamCode())
                .predictedPowerGenerationDto(PredictedPowerGenerationResponse.of(prediction))
                .predictedWaterStorageDto(PredictedWaterStorageResponse.of(prediction))
                .build();
    }
}
