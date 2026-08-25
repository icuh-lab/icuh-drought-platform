package re.kr.icuh.drought.persistence.openapi.agrimarket.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "daily_market_trends")
public class DailyMarketTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trend_date")
    private LocalDate trendDate;

    @Column(name = "location")
    private String location;

    @Column(name = "item")
    private String item;

    @Column(name = "variety")
    private String variety;

    @Column(name = "market_volume")
    private Long marketVolume;

    @Column(name = "avg_wholesale_price")
    private Integer avgWholesalePrice;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
