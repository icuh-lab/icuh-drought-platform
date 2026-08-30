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

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "drought_monthly_report_sido_status")
@IdClass(DroughtMonthlyReportSidoStatusId.class)
public class DroughtMonthlyReportSidoStatus {

    @Id
    @Column(name = "report_ym", length = 7)
    private String reportYm;

    @Id
    @Column(name = "sido", length = 10)
    private String sido;

    @Column(name = "detected", nullable = false)
    private boolean detected;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_grade")
    private ReportGrade maxGrade;
}
