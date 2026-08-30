package re.kr.icuh.drought.persistence.openapi.drought.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatus;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatusId;

import java.util.List;

@Repository
public interface DroughtMonthlyReportSidoStatusRepository
        extends JpaRepository<DroughtMonthlyReportSidoStatus, DroughtMonthlyReportSidoStatusId> {

    List<DroughtMonthlyReportSidoStatus> findByReportYm(String reportYm);

    List<DroughtMonthlyReportSidoStatus> findByReportYmAndDetectedTrue(String reportYm);
}
