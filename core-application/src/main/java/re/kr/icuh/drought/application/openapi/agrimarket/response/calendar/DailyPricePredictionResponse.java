package re.kr.icuh.drought.application.openapi.agrimarket.response.calendar;

import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.DailyPricePrediction;
import lombok.Builder;

import java.util.List;

public record DailyPricePredictionResponse(
    String location,
    String item,
    String variety,
    List<CalendarDataResponse> calendarData
)
{
    @Builder
    public DailyPricePredictionResponse {}

    public static DailyPricePredictionResponse of(List<DailyPricePrediction> predictions) {
        DailyPricePrediction first = predictions.get(0);
        return DailyPricePredictionResponse.builder()
                .location(first.getLocation())
                .item(first.getItem())
                .variety(first.getVariety())
                .calendarData(predictions.stream().map(CalendarDataResponse::of).toList())
                .build();
    }
}
