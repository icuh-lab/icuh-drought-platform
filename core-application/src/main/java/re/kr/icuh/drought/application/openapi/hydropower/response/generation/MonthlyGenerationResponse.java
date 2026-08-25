package re.kr.icuh.drought.application.openapi.hydropower.response.generation;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyGeneration;
import lombok.Builder;

public record MonthlyGenerationResponse(
    String year,
    String month,
    Integer plannedMwh,
    Integer actualMwh
) {
    @Builder
    public MonthlyGenerationResponse {}

    public static MonthlyGenerationResponse of(DamMonthlyGeneration generation) {
        return MonthlyGenerationResponse.builder()
                .year(generation.getYear())
                .month(generation.getMonth())
                .plannedMwh(generation.getPlannedMwh())
                .actualMwh(generation.getActualMwh())
                .build();
    }
}
