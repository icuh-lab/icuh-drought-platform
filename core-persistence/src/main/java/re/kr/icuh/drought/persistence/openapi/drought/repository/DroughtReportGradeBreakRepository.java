package re.kr.icuh.drought.persistence.openapi.drought.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtReportGradeBreak;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtReportGradeBreakId;

import java.util.List;
import java.util.Optional;

@Repository
public interface DroughtReportGradeBreakRepository
        extends JpaRepository<DroughtReportGradeBreak, DroughtReportGradeBreakId> {

    @Query("SELECT MAX(b.version) FROM DroughtReportGradeBreak b")
    Optional<Integer> findMaxVersion();

    List<DroughtReportGradeBreak> findByVersion(Integer version);
}
