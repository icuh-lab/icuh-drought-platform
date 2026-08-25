package re.kr.icuh.drought.application.openapi.hydropower.response.reservoir;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyReservoirStatus;
import lombok.Builder;

import java.util.List;

public record DamMonthlyReservoirStatusResponse(
    String damName,
    String damCode,
    List<MonthlyReservoirStatusResponse> monthlyReservoirStatusDto
) {
    @Builder
    public DamMonthlyReservoirStatusResponse {}

    public static DamMonthlyReservoirStatusResponse of(List<DamMonthlyReservoirStatus> statuses) {
        DamMonthlyReservoirStatus first = statuses.get(0);
        return DamMonthlyReservoirStatusResponse.builder()
                .damName(first.getDamName())
                .damCode(first.getDamCode())
                .monthlyReservoirStatusDto(statuses.stream().map(MonthlyReservoirStatusResponse::of).toList())
                .build();
    }
}
