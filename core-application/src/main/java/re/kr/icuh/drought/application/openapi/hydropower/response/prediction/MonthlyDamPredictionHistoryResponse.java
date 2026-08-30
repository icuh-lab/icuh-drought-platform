package re.kr.icuh.drought.application.openapi.hydropower.response.prediction;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyPrediction;
import lombok.Builder;

import java.util.List;

public record MonthlyDamPredictionHistoryResponse(
    String damName,
    String damCode,
    List<MonthlyPredictionHistoryEntryResponse> entries
) {
    @Builder
    public MonthlyDamPredictionHistoryResponse {}

    public static MonthlyDamPredictionHistoryResponse of(List<DamMonthlyPrediction> predictions) {
        DamMonthlyPrediction first = predictions.get(0);
        return MonthlyDamPredictionHistoryResponse.builder()
                .damName(first.getDamName())
                .damCode(first.getDamCode())
                .entries(predictions.stream().map(MonthlyPredictionHistoryEntryResponse::of).toList())
                .build();
    }
}
