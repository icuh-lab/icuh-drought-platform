package re.kr.icuh.drought.persistence.openapi.drought.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import re.kr.icuh.drought.domain.drought.ReportGrade;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DroughtReportGradeBreakId implements Serializable {
    private Integer version;
    private String impactCode;
    private ReportGrade grade;
}
