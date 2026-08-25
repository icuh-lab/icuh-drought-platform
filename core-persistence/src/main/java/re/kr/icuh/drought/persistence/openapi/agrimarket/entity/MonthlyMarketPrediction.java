package re.kr.icuh.drought.persistence.openapi.agrimarket.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "monthly_market_predictions")
public class MonthlyMarketPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location")
    private String location;

    @Column(name = "item")
    private String item;

    @Column(name = "variety")
    private String variety;

    @Column(name = "prediction_year")
    private String predictionYear;

    @Column(name = "prediction_month")
    private String predictionMonth;

    @Column(name = "predicted_price_lower_bound")
    private Integer predictedPriceLowerBound;

    @Column(name = "predicted_price_upper_bound")
    private Integer predictedPriceUpperBound;

    @Column(name = "prev_year_avg_price")
    private Integer prevYearAvgPrice;

    @Column(name = "price_change_from_prev_year")
    private Integer priceChangeFromPrevYear;

    @Column(name = "price_rate_of_change")
    private Integer priceRateOfChange;

    @Column(name = "price_indicator_color")
    private String priceIndicatorColor;

    @Column(name = "predicted_volume_lower_bound")
    private Integer predictedVolumeLowerBound;

    @Column(name = "predicted_volume_upper_bound")
    private Integer predictedVolumeUpperBound;

    @Column(name = "prev_year_avg_volume")
    private Integer prevYearAvgVolume;

    @Column(name = "volume_change_from_prev_year")
    private Integer volumeChangeFromPrevYear;

    @Column(name = "volume_rate_of_change")
    private Integer volumeRateOfChange;

    @Column(name = "volume_indicator_color")
    private String volumeIndicatorColor;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
