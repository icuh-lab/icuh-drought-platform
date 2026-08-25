package re.kr.icuh.drought.persistence.openapi.hydropower.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "dam_monthly_predictions")
public class DamMonthlyPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dam_name")
    private String damName;

    @Column(name = "dam_code")
    private String damCode;

    @Column(name = "year")
    private String year;

    @Column(name = "month")
    private String month;

    @Column(name = "predicted_power_generation_lower_bound")
    private Integer predictedPowerGenerationLowerBound;

    @Column(name = "predicted_power_generation_upper_bound")
    private Integer predictedPowerGenerationUpperBound;

    @Column(name = "predicted_water_storage_lower_bound")
    private Integer predictedWaterStorageLowerBound;

    @Column(name = "predicted_water_storage_upper_bound")
    private Integer predictedWaterStorageUpperBound;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
