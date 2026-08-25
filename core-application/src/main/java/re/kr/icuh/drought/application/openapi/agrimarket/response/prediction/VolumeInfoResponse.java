package re.kr.icuh.drought.application.openapi.agrimarket.response.prediction;

import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.MonthlyMarketPrediction;
import lombok.Builder;

public record VolumeInfoResponse(
   Integer predictedVolumeLowerBound,
   Integer predictedVolumeUpperBound,
   Integer prevYearAvgVolume,
   Integer volumeChangeFromPrevYear,
   Integer volumeRateOfChange,
   String volumeIndicatorColor
) {
    @Builder
    public VolumeInfoResponse {}

    public static VolumeInfoResponse of(MonthlyMarketPrediction prediction) {
        return VolumeInfoResponse.builder()
                .predictedVolumeLowerBound(prediction.getPredictedVolumeLowerBound())
                .predictedVolumeUpperBound(prediction.getPredictedVolumeUpperBound())
                .prevYearAvgVolume(prediction.getPrevYearAvgVolume())
                .volumeChangeFromPrevYear(prediction.getVolumeChangeFromPrevYear())
                .volumeRateOfChange(prediction.getVolumeRateOfChange())
                .volumeIndicatorColor(prediction.getVolumeIndicatorColor())
                .build();
    }
}
