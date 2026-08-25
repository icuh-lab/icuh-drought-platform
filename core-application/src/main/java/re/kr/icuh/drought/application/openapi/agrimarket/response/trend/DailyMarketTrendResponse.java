package re.kr.icuh.drought.application.openapi.agrimarket.response.trend;

import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.DailyMarketTrend;
import lombok.Builder;

import java.util.List;

public record DailyMarketTrendResponse(
    String location,
    String item,
    String variety,
    List<MonthlyTrendResponse> monthlyTrend
)
{
    @Builder
    public DailyMarketTrendResponse {}

    public static DailyMarketTrendResponse of(List<DailyMarketTrend> trends) {
        DailyMarketTrend first = trends.get(0);
        return DailyMarketTrendResponse.builder()
                .location(first.getLocation())
                .item(first.getItem())
                .variety(first.getVariety())
                .monthlyTrend(trends.stream().map(MonthlyTrendResponse::of).toList())
                .build();
    }
}
