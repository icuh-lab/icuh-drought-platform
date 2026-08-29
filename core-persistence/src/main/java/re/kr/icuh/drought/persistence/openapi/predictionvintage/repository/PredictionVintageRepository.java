package re.kr.icuh.drought.persistence.openapi.predictionvintage.repository;

import re.kr.icuh.drought.persistence.openapi.predictionvintage.entity.PredictionVintageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PredictionVintageRepository extends JpaRepository<PredictionVintageLog, Long> {

    List<PredictionVintageLog> findByLocationOrderByTargetDateAsc(String location);
}
