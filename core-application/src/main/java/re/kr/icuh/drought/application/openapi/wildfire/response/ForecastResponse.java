package re.kr.icuh.drought.application.openapi.wildfire.response;

import re.kr.icuh.drought.persistence.openapi.wildfire.Sigungu;

import java.util.List;

/**
 * {
 *       "targetDate": "2023-03-21",
 *       "targetTime": "15:00",
 *       "regionData": [
 *         { "regionCode": "11000", "riskLevel": "high", "indexValue": 82 }, // riskLevel: very_high, high, moderate, low
 *         { "regionCode": "26000", "riskLevel": "moderate", "indexValue": 65 }
 *       ]
 *     }
 */

public record ForecastResponse(
        String targetDate,
        String targetTime,
        List<Sigungu> regionData
) {
}
