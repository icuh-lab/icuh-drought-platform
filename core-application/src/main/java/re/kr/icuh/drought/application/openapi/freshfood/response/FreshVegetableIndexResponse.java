package re.kr.icuh.drought.application.openapi.freshfood.response;

import re.kr.icuh.drought.persistence.openapi.freshfood.FreshVegetableIndex;

import java.util.List;
import java.util.Map;

public record FreshVegetableIndexResponse(
    String baseDate,
    List<FreshVegetableIndex> provinceData,
    Map<String, Long> summary
) {
    public static FreshVegetableIndexResponse of(String baseDate, List<FreshVegetableIndex> provinceData, Map<String, Long> summary) {
        return new FreshVegetableIndexResponse(baseDate, provinceData, summary);
    }
}
