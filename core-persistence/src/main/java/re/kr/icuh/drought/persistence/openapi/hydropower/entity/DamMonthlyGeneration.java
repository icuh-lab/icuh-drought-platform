package re.kr.icuh.drought.persistence.openapi.hydropower.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "dam_monthly_generation")
public class DamMonthlyGeneration {

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

    @Column(name = "planned_mwh")
    private Integer plannedMwh;

    @Column(name = "actual_mwh")
    private Integer actualMwh;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
