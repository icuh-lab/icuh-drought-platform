package re.kr.icuh.drought.persistence.openapi.wildfire.repository;

import re.kr.icuh.drought.persistence.openapi.wildfire.entity.NewsArticle;
import re.kr.icuh.drought.persistence.openapi.wildfire.entity.WildFireRiskIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Repository
public interface WildFireRiskIndexRepository extends JpaRepository<WildFireRiskIndex, Long> {


    // analdate 기준으로 동일한 시간대에 3일치 데이터를 가져오는 쿼리문
    // ex) 2025-12-17 11:00:00, 2025-12-18 11:00:00, 2025-12-19 11:00:00 (start와 end 값을 파라미터로 넘겨주자)
    @Query("SELECT w FROM WildFireRiskIndex w WHERE w.analdate = :date")
    List<WildFireRiskIndex> findByAnaldate(LocalDateTime date);

    @Query("SELECT n FROM NewsArticle n WHERE n.publishDate BETWEEN :startDate AND :endDate")
    List<NewsArticle> findNewsArticleByPublishDate(LocalDate startDate, LocalDate endDate);
}
