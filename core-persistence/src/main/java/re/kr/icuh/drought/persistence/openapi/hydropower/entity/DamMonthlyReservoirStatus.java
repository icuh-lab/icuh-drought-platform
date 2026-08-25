package re.kr.icuh.drought.persistence.openapi.hydropower.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "dam_monthly_reservoir_status")
public class DamMonthlyReservoirStatus {

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

    @Column(name = "water_level_elm")
    private Integer waterLevelElm;

    @Column(name = "water_storage_mcm")
    private Integer waterStorageMcm;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
