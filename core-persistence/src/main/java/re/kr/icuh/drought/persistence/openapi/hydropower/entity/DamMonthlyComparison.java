package re.kr.icuh.drought.persistence.openapi.hydropower.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "dam_monthly_comparison")
public class DamMonthlyComparison {

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

    @Column(name = "hydro_generation_last_year_month_amount")
    private Integer hydroGenerationLastYearMonthAmount;

    @Column(name = "hydro_generation_last_year_month_status")
    private String hydroGenerationLastYearMonthStatus;

    @Column(name = "hydro_generation_last_year_month_rate")
    private Integer hydroGenerationLastYearMonthRate;

    @Column(name = "hydro_generation_last_year_month_color")
    private String hydroGenerationLastYearMonthColor;

    @Column(name = "hydro_generation_last_month_amount")
    private Integer hydroGenerationLastMonthAmount;

    @Column(name = "hydro_generation_last_month_status")
    private String hydroGenerationLastMonthStatus;

    @Column(name = "hydro_generation_last_month_rate")
    private Integer hydroGenerationLastMonthRate;

    @Column(name = "hydro_generation_last_month_color")
    private String hydroGenerationLastMonthColor;

    @Column(name = "average_water_storage_last_month_amount")
    private Integer averageWaterStorageLastMonthAmount;

    @Column(name = "average_water_storage_last_month_status")
    private String averageWaterStorageLastMonthStatus;

    @Column(name = "average_water_storage_last_month_rate")
    private Integer averageWaterStorageLastMonthRate;

    @Column(name = "average_water_storage_last_month_color")
    private String averageWaterStorageLastMonthColor;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
