package re.kr.icuh.drought.application.openapi.hydropower.response.comparison;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyComparison;
import lombok.Builder;

public record HydroGenerationLastYearMonthResponse(
    Integer hydroGenerationLastYearMonthAmount,
    String hydroGenerationLastYearMonthStatus,
    Integer hydroGenerationLastYearMonthRate,
    String hydroGenerationLastYearMonthColor
) {
    @Builder
    public HydroGenerationLastYearMonthResponse {}

    public static HydroGenerationLastYearMonthResponse of(DamMonthlyComparison comparison) {
        return HydroGenerationLastYearMonthResponse.builder()
                .hydroGenerationLastYearMonthAmount(comparison.getHydroGenerationLastYearMonthAmount())
                .hydroGenerationLastYearMonthStatus(comparison.getHydroGenerationLastYearMonthStatus())
                .hydroGenerationLastYearMonthRate(comparison.getHydroGenerationLastYearMonthRate())
                .hydroGenerationLastYearMonthColor(comparison.getHydroGenerationLastYearMonthColor())
                .build();
    }
}
