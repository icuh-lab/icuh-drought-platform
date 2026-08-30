package re.kr.icuh.drought.application.openapi.drought.response;

import re.kr.icuh.drought.domain.drought.ReportGrade;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record RegionSectionResponse(String sido, String sigungu, List<ImpactBucketResponse> impactFields) {
    public static RegionSectionResponse of(
            String sido,
            String sigungu,
            List<DroughtMonthlyReportBucket> buckets,
            Map<String, Map<ReportGrade, Double>> gradeBreaksByImpactCode
    ) {
        List<ImpactBucketResponse> fields = buckets.stream()
                .sorted(Comparator.comparing(DroughtMonthlyReportBucket::getImpactCode))
                .map(b -> ImpactBucketResponse.of(b, gradeBreaksByImpactCode.getOrDefault(b.getImpactCode(), Map.of())))
                .toList();
        return new RegionSectionResponse(sido, sigungu, fields);
    }
}
