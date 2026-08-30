package re.kr.icuh.drought.persistence.openapi.drought.entity;

import jakarta.persistence.Column;
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

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "drought_report_grade_breaks")
@IdClass(DroughtReportGradeBreakId.class)
public class DroughtReportGradeBreak {

    @Id
    @Column(name = "version", nullable = false)
    private Integer version;

    @Id
    @Column(name = "impact_code", length = 2, nullable = false)
    private String impactCode;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false)
    private ReportGrade grade;

    @Column(name = "lower_bound", nullable = false)
    private Double lowerBound;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
