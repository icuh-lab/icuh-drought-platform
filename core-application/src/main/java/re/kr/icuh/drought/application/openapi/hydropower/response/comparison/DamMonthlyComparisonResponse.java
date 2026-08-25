package re.kr.icuh.drought.application.openapi.hydropower.response.comparison;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyComparison;
import lombok.Builder;

public record DamMonthlyComparisonResponse(
    String damName,
    String damCode,
    HydroGenerationLastYearMonthResponse hydroGenerationLastYearMonthDto,
    HydroGenerationLastMonthResponse hydroGenerationLastMonthDto,
    AverageWaterStorageLastMonthResponse averageWaterStorageLastMonthDto
) {
    @Builder
    public DamMonthlyComparisonResponse {}

    public static DamMonthlyComparisonResponse of(DamMonthlyComparison comparison) {
        return DamMonthlyComparisonResponse.builder()
                .damName(comparison.getDamName())
                .damCode(comparison.getDamCode())
                .hydroGenerationLastYearMonthDto(HydroGenerationLastYearMonthResponse.of(comparison))
                .hydroGenerationLastMonthDto(HydroGenerationLastMonthResponse.of(comparison))
                .averageWaterStorageLastMonthDto(AverageWaterStorageLastMonthResponse.of(comparison))
                .build();
    }
}
