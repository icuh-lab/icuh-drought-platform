package re.kr.icuh.drought.persistence.openapi.freshfood;

import re.kr.icuh.drought.persistence.openapi.freshfood.entity.FreshFood;

public record FreshFruitIndex(
        Integer code,
        String province,
        Float freshFruitIndex,
        String grade
) {
    public static FreshFruitIndex of(FreshFood freshFood) {
        String grade = FreshFoodGrade.labelOf(freshFood.getFreshFruitIndex());

        return new FreshFruitIndex(
                Province.findCodeByName(freshFood.getProvince()),
                freshFood.getProvince(),
                freshFood.getFreshFruitIndex(),
                grade
        );
    }
}
