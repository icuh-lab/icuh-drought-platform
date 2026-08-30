package re.kr.icuh.drought.application.openapi.drought.response;

import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;

import java.util.Comparator;
import java.util.List;

public record RegionSectionResponse(String sido, String sigungu, List<ImpactBucketResponse> impactFields) {
    public static RegionSectionResponse of(String sido, String sigungu, List<DroughtMonthlyReportBucket> buckets) {
        List<ImpactBucketResponse> fields = buckets.stream()
                .sorted(Comparator.comparing(DroughtMonthlyReportBucket::getImpactCode))
                .map(ImpactBucketResponse::of)
                .toList();
        return new RegionSectionResponse(sido, sigungu, fields);
    }
}
