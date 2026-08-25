package re.kr.icuh.drought.persistence.openapi.agrimarket.repository;

import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.DailyMarketTrend;
import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.DailyPricePrediction;
import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.MonthlyMarketPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgriMarketRepository extends JpaRepository<MonthlyMarketPrediction, Long> {

    @Query("SELECT m FROM MonthlyMarketPrediction m WHERE m.predictionYear = :year AND m.predictionMonth = :month AND m.location = :location")
    Optional<MonthlyMarketPrediction> agriMarketPricePredict(@Param("year") String year, @Param("month") String month, @Param("location") String location);

    @Query("SELECT d FROM DailyPricePrediction d WHERE YEAR(d.predictionDate) = :year AND MONTH(d.predictionDate) = :month AND d.location = :location")
    List<DailyPricePrediction> dailyPricePrediction(@Param("year") String year, @Param("month") String month, @Param("location") String location);

    @Query("SELECT d FROM DailyMarketTrend d WHERE YEAR(d.trendDate) = :year AND MONTH(d.trendDate) = :month AND d.location = :location")
    List<DailyMarketTrend> dailyMarketTrend(@Param("year") String year, @Param("month") String month, @Param("location") String location);
}
