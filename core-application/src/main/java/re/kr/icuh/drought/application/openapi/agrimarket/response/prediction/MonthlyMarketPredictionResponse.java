package re.kr.icuh.drought.application.openapi.agrimarket.response.prediction;

import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.MonthlyMarketPrediction;
import lombok.Builder;


public record MonthlyMarketPredictionResponse(
    String year,
    String month,
    String location,
    PriceInfoResponse priceInfo,
    VolumeInfoResponse volumeInfo
) {
    @Builder
    public MonthlyMarketPredictionResponse {}

    public static MonthlyMarketPredictionResponse of(MonthlyMarketPrediction prediction) {
        return MonthlyMarketPredictionResponse.builder()
                .year(prediction.getPredictionYear())
                .month(prediction.getPredictionMonth())
                .location(prediction.getLocation())
                .priceInfo(PriceInfoResponse.of(prediction))
                .volumeInfo(VolumeInfoResponse.of(prediction))
                .build();
    }
}
