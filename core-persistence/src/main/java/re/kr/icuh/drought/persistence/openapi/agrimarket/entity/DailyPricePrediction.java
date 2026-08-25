package re.kr.icuh.drought.persistence.openapi.agrimarket.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "daily_price_predictions")
public class DailyPricePrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prediction_date")
    private LocalDate predictionDate;

    @Column(name = "location")
    private String location;

    @Column(name = "item")
    private String item;

    @Column(name = "variety")
    private String variety;

    @Column(name = "predicted_price")
    private Integer predictedPrice;

    @Column(name = "rate_of_change_from_prev_year")
    private Integer rateOfChangeFromPrevYear;

    @Column(name = "change_description")
    private String changeDescription;

    @Column(name = "indicator_color")
    private String indicatorColor;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
