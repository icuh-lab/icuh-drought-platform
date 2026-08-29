package re.kr.icuh.drought.persistence.openapi.hydropower.repository;

import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyComparison;
import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyGeneration;
import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyPrediction;
import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyReservoirStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HydroPowerRepository extends JpaRepository<DamMonthlyPrediction, Long> {

    @Query("SELECT d FROM DamMonthlyPrediction d WHERE d.year = :year AND d.month = :month AND d.damName = :damName")
    Optional<DamMonthlyPrediction> damMonthlyPrediction(@Param("year") String year, @Param("month") String month, @Param("damName") String damName);

    @Query("SELECT d FROM DamMonthlyComparison d WHERE d.year = :year AND d.month = :month AND d.damName = :damName")
    Optional<DamMonthlyComparison> damMonthlyComparison(@Param("year") String year, @Param("month") String month, @Param("damName") String damName);

    @Query("SELECT d FROM DamMonthlyGeneration d WHERE d.year = :year AND d.damName = :damName ORDER BY CAST(d.month AS integer) ASC")
    List<DamMonthlyGeneration> damMonthlyGeneration(@Param("year") String year, @Param("damName") String damName);

    @Query("SELECT d FROM DamMonthlyReservoirStatus d WHERE d.year = :year AND d.damName = :damName ORDER BY CAST(d.month AS integer) ASC")
    List<DamMonthlyReservoirStatus> damMonthlyReservoirStatus(@Param("year") String year, @Param("damName") String damName);
}
