package re.kr.icuh.drought.persistence.openapi.freshfood;

import re.kr.icuh.drought.persistence.openapi.freshfood.entity.FreshFood;

public record FreshVegetableIndex(
        Integer code,
        String province,
        Float freshVegetableIndex,
        String grade
) {
    public static FreshVegetableIndex of(FreshFood freshFood) {
        String grade = FreshFoodGrade.labelOf(freshFood.getFreshVegetableIndex());

        return new FreshVegetableIndex(
                Province.findCodeByName(freshFood.getProvince()),
                freshFood.getProvince(),
                freshFood.getFreshVegetableIndex(),
                grade
        );
    }
}
