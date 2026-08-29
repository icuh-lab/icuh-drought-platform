package re.kr.icuh.drought.persistence.openapi.predictionvintage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "onion_prediction_vintage_log")
public class PredictionVintageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "region")
    private String region;

    @Column(name = "location")
    private String location;

    @Column(name = "item")
    private String item;

    @Column(name = "variety")
    private String variety;

    @Column(name = "horizon_days")
    private Integer horizonDays;

    @Column(name = "model_type")
    private String modelType;

    @Column(name = "model_train_end_date")
    private LocalDate modelTrainEndDate;

    @Column(name = "source")
    private String source;

    @Column(name = "pred")
    private BigDecimal pred;

    @Column(name = "pred_created_at")
    private LocalDateTime predCreatedAt;

    @Column(name = "actual")
    private BigDecimal actual;

    @Column(name = "actual_updated_at")
    private LocalDateTime actualUpdatedAt;

    @Column(name = "arrival_ton")
    private BigDecimal arrivalTon;
}
