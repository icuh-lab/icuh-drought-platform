package re.kr.icuh.drought.application.openapi.hydropower.response.generation;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyGeneration;
import lombok.Builder;

import java.util.List;

public record DamMonthlyGenerationResponse(
    String damName,
    String damCode,
    List<MonthlyGenerationResponse> monthlyGenerationDto
) {
    @Builder
    public DamMonthlyGenerationResponse {}

    public static DamMonthlyGenerationResponse of(List<DamMonthlyGeneration> generations) {
        DamMonthlyGeneration first = generations.get(0);
        return DamMonthlyGenerationResponse.builder()
                .damName(first.getDamName())
                .damCode(first.getDamCode())
                .monthlyGenerationDto(generations.stream().map(MonthlyGenerationResponse::of).toList())
                .build();
    }
}
