package re.kr.icuh.drought.application.openapi.hydropower.response.prediction;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyPrediction;
import lombok.Builder;

public record PredictedWaterStorageResponse(
    Integer predictedWaterStorageLowerBound,
    Integer predictedWaterStorageUpperBound
) {
    @Builder
    public PredictedWaterStorageResponse {}

    public static PredictedWaterStorageResponse of(DamMonthlyPrediction prediction) {
        return PredictedWaterStorageResponse.builder()
                .predictedWaterStorageLowerBound(prediction.getPredictedWaterStorageLowerBound())
                .predictedWaterStorageUpperBound(prediction.getPredictedWaterStorageUpperBound())
                .build();
    }
}
