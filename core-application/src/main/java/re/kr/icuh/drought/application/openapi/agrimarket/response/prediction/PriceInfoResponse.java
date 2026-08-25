package re.kr.icuh.drought.application.openapi.agrimarket.response.prediction;

import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.MonthlyMarketPrediction;
import lombok.Builder;

public record PriceInfoResponse(
   Integer predictedPriceLowerBound,
   Integer predictedPriceUpperBound,
   Integer prevYearAvgPrice,
   Integer priceChangeFromPrevYear,
   Integer priceRateOfChange,
   String priceIndicatorColor
) {
    @Builder
    public PriceInfoResponse{}

    public static PriceInfoResponse of(MonthlyMarketPrediction prediction) {
        return PriceInfoResponse.builder()
                .predictedPriceLowerBound(prediction.getPredictedPriceLowerBound())
                .predictedPriceUpperBound(prediction.getPredictedPriceUpperBound())
                .prevYearAvgPrice(prediction.getPrevYearAvgPrice())
                .priceChangeFromPrevYear(prediction.getPriceChangeFromPrevYear())
                .priceRateOfChange(prediction.getPriceRateOfChange())
                .priceIndicatorColor(prediction.getPriceIndicatorColor())
                .build();
    }
}
