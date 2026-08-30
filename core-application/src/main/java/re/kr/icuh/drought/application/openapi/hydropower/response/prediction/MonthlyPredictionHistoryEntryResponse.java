package re.kr.icuh.drought.application.openapi.hydropower.response.prediction;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyPrediction;
import lombok.Builder;

public record MonthlyPredictionHistoryEntryResponse(
    String year,
    String month,
    Integer predictedPowerGenerationLowerBound,
    Integer predictedPowerGenerationUpperBound,
    Integer predictedWaterStorageLowerBound,
    Integer predictedWaterStorageUpperBound
) {
    @Builder
    public MonthlyPredictionHistoryEntryResponse {}

    public static MonthlyPredictionHistoryEntryResponse of(DamMonthlyPrediction prediction) {
        return MonthlyPredictionHistoryEntryResponse.builder()
                .year(prediction.getYear())
                .month(prediction.getMonth())
                .predictedPowerGenerationLowerBound(prediction.getPredictedPowerGenerationLowerBound())
                .predictedPowerGenerationUpperBound(prediction.getPredictedPowerGenerationUpperBound())
                .predictedWaterStorageLowerBound(prediction.getPredictedWaterStorageLowerBound())
                .predictedWaterStorageUpperBound(prediction.getPredictedWaterStorageUpperBound())
                .build();
    }
}
