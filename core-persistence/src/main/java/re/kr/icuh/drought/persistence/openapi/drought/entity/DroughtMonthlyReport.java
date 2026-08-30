package re.kr.icuh.drought.persistence.openapi.drought.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "drought_monthly_report")
public class DroughtMonthlyReport {

    @Id
    @Column(name = "report_ym", length = 7)
    private String reportYm;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "article_count", nullable = false)
    private int articleCount;

    @Column(name = "detected_sido_count", nullable = false)
    private int detectedSidoCount;
}
