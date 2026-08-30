package re.kr.icuh.drought.persistence.openapi.drought.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucket;
import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportBucketId;

import java.util.List;

@Repository
public interface DroughtMonthlyReportBucketRepository
        extends JpaRepository<DroughtMonthlyReportBucket, DroughtMonthlyReportBucketId> {

    List<DroughtMonthlyReportBucket> findByReportYm(String reportYm);
}
