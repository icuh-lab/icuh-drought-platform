package re.kr.icuh.drought.application.openapi.drought.response;

import re.kr.icuh.drought.domain.drought.DroughtImpactField;
import re.kr.icuh.drought.domain.drought.ReportGrade;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;

import java.util.List;
import java.util.Map;

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
        int continuityCount,
        Double gradeLowerBound,
        Double nextGradeLowerBound
) {
    /**
     * gradeBreaks: 이 버킷의 impact_code에 해당하는, 최신 재보정 버전의 등급별 하한값(관심/주의/경계/심각 → 기사건수).
     * 아직 재보정이 한 번도 안 됐거나 해당 영향분야의 구간이 없으면 빈 맵 — gradeLowerBound/nextGradeLowerBound는 null로 내려간다.
     * <p>
     * 확정 전(gradeFinalized=false) 버킷은 grade가 항상 관심 placeholder라 이 기준선과 무관하다 — 붙이면
     * "관심 등급인데 근거는 다음 등급 문턱"처럼 앞뒤가 안 맞는 값이 나가므로, 확정된 버킷에만 채운다.
     */
    public static ImpactBucketResponse of(DroughtMonthlyReportBucket bucket, Map<ReportGrade, Double> gradeBreaks) {
        ReportGrade grade = bucket.getGrade();
        boolean gradeFinalized = bucket.getGradeFinalizedAt() != null;
        ReportGrade[] grades = ReportGrade.values();
        Double gradeLowerBound = gradeFinalized ? gradeBreaks.get(grade) : null;
        Double nextGradeLowerBound = gradeFinalized && grade.ordinal() + 1 < grades.length
                ? gradeBreaks.get(grades[grade.ordinal() + 1])
                : null;

        return new ImpactBucketResponse(
                bucket.getImpactCode(),
                DroughtImpactField.fromCode(bucket.getImpactCode()).displayName(),
                grade.name(),
                gradeFinalized,
                bucket.getArticleCount(),
                bucket.getRepresentativeTitle(),
                bucket.getRepresentativeLink(),
                bucket.getKeywords(),
                bucket.isRelevanceFlag(),
                bucket.getContinuityCount(),
                gradeLowerBound,
                nextGradeLowerBound
        );
    }
}
