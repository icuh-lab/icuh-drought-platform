package re.kr.icuh.drought.application.openapi.hydropower.response.reservoir;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyReservoirStatus;
import lombok.Builder;

public record MonthlyReservoirStatusResponse(
    String year,
    String month,
    Integer waterLevelElm,
    Integer waterStorageMcm
) {
    @Builder
    public MonthlyReservoirStatusResponse {}

    public static MonthlyReservoirStatusResponse of(DamMonthlyReservoirStatus status) {
        return MonthlyReservoirStatusResponse.builder()
                .year(status.getYear())
                .month(status.getMonth())
                .waterLevelElm(status.getWaterLevelElm())
                .waterStorageMcm(status.getWaterStorageMcm())
                .build();
    }
}
