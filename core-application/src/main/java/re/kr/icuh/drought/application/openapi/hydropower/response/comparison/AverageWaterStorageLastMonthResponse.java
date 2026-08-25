package re.kr.icuh.drought.application.openapi.hydropower.response.comparison;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyComparison;
import lombok.Builder;

public record AverageWaterStorageLastMonthResponse(
    Integer averageWaterStorageLastMonthAmount,
    String averageWaterStorageLastMonthStatus,
    Integer averageWaterStorageLastMonthRate,
    String averageWaterStorageLastMonthColor
) {
    @Builder
    public AverageWaterStorageLastMonthResponse {}

    public static AverageWaterStorageLastMonthResponse of(DamMonthlyComparison comparison) {
        return AverageWaterStorageLastMonthResponse.builder()
                .averageWaterStorageLastMonthAmount(comparison.getAverageWaterStorageLastMonthAmount())
                .averageWaterStorageLastMonthStatus(comparison.getAverageWaterStorageLastMonthStatus())
                .averageWaterStorageLastMonthRate(comparison.getAverageWaterStorageLastMonthRate())
                .averageWaterStorageLastMonthColor(comparison.getAverageWaterStorageLastMonthColor())
                .build();
    }
}
