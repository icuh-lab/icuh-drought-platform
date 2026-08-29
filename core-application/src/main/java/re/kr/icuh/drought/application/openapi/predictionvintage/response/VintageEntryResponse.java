package re.kr.icuh.drought.application.openapi.predictionvintage.response;

import re.kr.icuh.drought.persistence.openapi.predictionvintage.entity.PredictionVintageLog;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VintageEntryResponse(
    LocalDate targetDate,
    Integer horizonDays,
    String source,
    String modelType,
    LocalDate modelTrainEndDate,
    BigDecimal pred,
    BigDecimal actual,
    BigDecimal arrivalTon
) {
    @Builder
    public VintageEntryResponse {}

    public static VintageEntryResponse of(PredictionVintageLog log) {
        return VintageEntryResponse.builder()
                .targetDate(log.getTargetDate())
                .horizonDays(log.getHorizonDays())
                .source(log.getSource())
                .modelType(log.getModelType())
                .modelTrainEndDate(log.getModelTrainEndDate())
                .pred(log.getPred())
                .actual(log.getActual())
                .arrivalTon(log.getArrivalTon())
                .build();
    }
}
