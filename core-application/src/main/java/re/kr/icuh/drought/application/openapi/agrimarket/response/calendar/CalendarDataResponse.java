package re.kr.icuh.drought.application.openapi.agrimarket.response.calendar;

import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.DailyPricePrediction;
import lombok.Builder;

import java.time.LocalDate;

public record CalendarDataResponse(
    LocalDate predictionDate,
    Integer predictedPrice,
    Integer rateOfChangeFromPrevYear,
    String changeDescription,
    String indicatorColor
) {
    @Builder
    public CalendarDataResponse {}

    public static CalendarDataResponse of(DailyPricePrediction prediction) {
        return CalendarDataResponse.builder()
                .predictionDate(prediction.getPredictionDate())
                .predictedPrice(prediction.getPredictedPrice())
                .rateOfChangeFromPrevYear(prediction.getRateOfChangeFromPrevYear())
                .changeDescription(prediction.getChangeDescription())
                .indicatorColor(prediction.getIndicatorColor())
                .build();
    }
}
