package re.kr.icuh.drought.application.openapi.hydropower.response.comparison;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyComparison;
import lombok.Builder;

public record HydroGenerationLastMonthResponse(
    Integer hydroGenerationLastMonthAmount,
    String hydroGenerationLastMonthStatus,
    Integer hydroGenerationLastMonthRate,
    String hydroGenerationLastMonthColor
)
{
    @Builder
    public HydroGenerationLastMonthResponse {}

    public static HydroGenerationLastMonthResponse of(DamMonthlyComparison comparison) {
        return HydroGenerationLastMonthResponse.builder()
                .hydroGenerationLastMonthAmount(comparison.getHydroGenerationLastMonthAmount())
                .hydroGenerationLastMonthStatus(comparison.getHydroGenerationLastMonthStatus())
                .hydroGenerationLastMonthRate(comparison.getHydroGenerationLastMonthRate())
                .hydroGenerationLastMonthColor(comparison.getHydroGenerationLastMonthColor())
                .build();
    }
}
