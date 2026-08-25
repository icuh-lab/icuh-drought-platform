package re.kr.icuh.drought.persistence.openapi.wildfire;

import re.kr.icuh.drought.persistence.openapi.wildfire.entity.WildFireRiskIndex;

public record Sigungu(
        String regionCode,
        String riskLevel,
        Integer indexValue
) {
    public static Sigungu of(WildFireRiskIndex wildFireRiskIndex) {
        String riskLevel = createRiskLevel(wildFireRiskIndex.getMeanavg());

        return new Sigungu(
          wildFireRiskIndex.getRegioncode(),
          riskLevel,
          wildFireRiskIndex.getMeanavg()
        );
    }

    // very_high, high, moderate, low
    private static String createRiskLevel(Integer indexValue) {
        if (indexValue >= 87) return "very_high";
        if (indexValue >= 67) return "high";
        if (indexValue >= 52) return "moderate";
        return "low";
    }
}
