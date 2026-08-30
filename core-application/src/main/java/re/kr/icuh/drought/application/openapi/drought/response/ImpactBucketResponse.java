package re.kr.icuh.drought.application.openapi.drought.response;

import re.kr.icuh.drought.domain.drought.DroughtImpactField;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;

import java.util.List;

public record ImpactBucketResponse(
        String impactCode,
        String impactName,
        String grade,
        boolean gradeFinalized,
        int articleCount,
        String representativeTitle,
        String representativeLink,
        List<String> keywords,
        boolean relevanceFlag,
        int continuityCount
) {
    public static ImpactBucketResponse of(DroughtMonthlyReportBucket bucket) {
        return new ImpactBucketResponse(
                bucket.getImpactCode(),
                DroughtImpactField.fromCode(bucket.getImpactCode()).displayName(),
                bucket.getGrade().name(),
                bucket.getGradeFinalizedAt() != null,
                bucket.getArticleCount(),
                bucket.getRepresentativeTitle(),
                bucket.getRepresentativeLink(),
                bucket.getKeywords(),
                bucket.isRelevanceFlag(),
                bucket.getContinuityCount()
        );
    }
}
