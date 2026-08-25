package re.kr.icuh.drought.persistence.openapi.hydropower.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "dam_daily_generation")
public class DamDailyGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dam_name")
    private String damName;

    @Column(name = "dam_code")
    private String damCode;

    @Column(name = "generation_date")
    private LocalDate generationDate;

    @Column(name = "planned_mwh")
    private Integer plannedMwh;

    @Column(name = "actual_mwh")
    private Integer actualMwh;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
