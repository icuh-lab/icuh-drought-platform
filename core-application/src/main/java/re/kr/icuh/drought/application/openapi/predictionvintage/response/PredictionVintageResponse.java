package re.kr.icuh.drought.application.openapi.predictionvintage.response;

import re.kr.icuh.drought.persistence.openapi.predictionvintage.entity.PredictionVintageLog;
import lombok.Builder;

import java.util.List;

public record PredictionVintageResponse(
    String location,
    String item,
    String variety,
    List<VintageEntryResponse> entries
) {
    @Builder
    public PredictionVintageResponse {}

    public static PredictionVintageResponse of(List<PredictionVintageLog> logs) {
        PredictionVintageLog first = logs.get(0);
        return PredictionVintageResponse.builder()
                .location(first.getLocation())
                .item(first.getItem())
                .variety(first.getVariety())
                .entries(logs.stream().map(VintageEntryResponse::of).toList())
                .build();
    }
}
