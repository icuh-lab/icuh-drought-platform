package re.kr.icuh.drought.application.openapi.agrimarket.response.trend;

import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.DailyMarketTrend;
import lombok.Builder;

import java.time.LocalDate;

public record MonthlyTrendResponse(
    LocalDate trendDate,
    Long marketVolume,
    Integer avgWholesalePrice
)
{
    @Builder
    public MonthlyTrendResponse {}

    public static MonthlyTrendResponse of(DailyMarketTrend trend) {
        return MonthlyTrendResponse.builder()
                .trendDate(trend.getTrendDate())
                .marketVolume(trend.getMarketVolume())
                .avgWholesalePrice(trend.getAvgWholesalePrice())
                .build();
    }
}
