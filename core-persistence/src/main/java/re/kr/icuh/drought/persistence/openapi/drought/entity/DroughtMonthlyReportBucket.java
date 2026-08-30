package re.kr.icuh.drought.persistence.openapi.drought.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import re.kr.icuh.drought.domain.drought.ReportGrade;
import re.kr.icuh.drought.persistence.openapi.drought.converter.KeywordsJsonConverter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "drought_monthly_report_bucket")
@IdClass(DroughtMonthlyReportBucketId.class)
public class DroughtMonthlyReportBucket {

    @Id
    @Column(name = "report_ym", length = 7)
    private String reportYm;

    @Id
    @Column(name = "sido", length = 20)
    private String sido;

    @Id
    @Column(name = "sigungu", length = 30)
    private String sigungu;

    @Id
    @Column(name = "impact_code", length = 2)
    private String impactCode;

    @Column(name = "article_count", nullable = false)
    private int articleCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false)
    private ReportGrade grade;

    @Column(name = "grade_finalized_at")
    private LocalDateTime gradeFinalizedAt;

    @Column(name = "representative_link", length = 700)
    private String representativeLink;

    @Column(name = "representative_title", length = 500)
    private String representativeTitle;

    @Convert(converter = KeywordsJsonConverter.class)
    @Column(name = "keywords", columnDefinition = "TEXT")
    private List<String> keywords;

    @Column(name = "relevance_flag", nullable = false)
    private boolean relevanceFlag;

    @Column(name = "continuity_count", nullable = false)
    private int continuityCount;
}
