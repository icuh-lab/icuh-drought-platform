package re.kr.icuh.drought.application.openapi.freshfood.response;

import re.kr.icuh.drought.persistence.openapi.freshfood.FreshFruitIndex;

import java.util.List;
import java.util.Map;

public record FreshFruitIndexResponse(
        String baseDate,
        List<FreshFruitIndex> provinceData,
        Map<String, Long> summary
) {
    public static FreshFruitIndexResponse of(String baseDate, List<FreshFruitIndex> provinceData, Map<String, Long> summary) {
        return new FreshFruitIndexResponse(baseDate, provinceData, summary);
    }
}
