package re.kr.icuh.drought.persistence.openapi.freshfood.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name = "drought_impact_fresh_food_price_index")
public class FreshFood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String province;
    private String baseDate;
    private Float totalIndex;
    private Float freshFoodIndex;
    private Float freshFishIndex;
    private Float freshVegetableIndex;
    private Float freshFruitIndex;
    private Float excludingFreshFoodIndex;
}
