package re.kr.icuh.drought.persistence.openapi.drought.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReport;

import java.util.Optional;

@Repository
public interface DroughtMonthlyReportRepository extends JpaRepository<DroughtMonthlyReport, String> {

    Optional<DroughtMonthlyReport> findTopByOrderByReportYmDesc();
}
