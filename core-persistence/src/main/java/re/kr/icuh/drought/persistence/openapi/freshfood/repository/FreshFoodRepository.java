package re.kr.icuh.drought.persistence.openapi.freshfood.repository;

import re.kr.icuh.drought.persistence.openapi.freshfood.entity.FreshFood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FreshFoodRepository extends JpaRepository<FreshFood, Long> {

    List<FreshFood> findByBaseDate(String baseDate);
}
