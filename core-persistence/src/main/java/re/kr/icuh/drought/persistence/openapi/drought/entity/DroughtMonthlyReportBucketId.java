package re.kr.icuh.drought.persistence.openapi.drought.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DroughtMonthlyReportBucketId implements Serializable {
    private String reportYm;
    private String sido;
    private String sigungu;
    private String impactCode;
}
