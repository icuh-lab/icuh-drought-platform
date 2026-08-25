package re.kr.icuh.drought.persistence.openapi.freshfood;

/**
 * 신선식품 지수(채소/과일 공통)를 등급으로 분류한다.
 * 임계값은 {@code float} 리터럴로 두어, {@code Float} 지수와 같은 타입으로 비교한다
 * (double 리터럴과 비교하면 85.1f -> 85.0999.. 처럼 경계에서 어긋날 수 있음).
 */
public enum FreshFoodGrade {

    VERY_HIGH(115.1f, "veryHigh"),
    HIGH(105.1f, "high"),
    NORMAL(95.1f, "normal"),
    LOW(85.1f, "low"),
    VERY_LOW(Float.NEGATIVE_INFINITY, "veryLow");

    private final float threshold;
    private final String label;

    FreshFoodGrade(float threshold, String label) {
        this.threshold = threshold;
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 지수값을 등급 라벨로 변환한다. 임계값은 내림차순으로 평가한다. */
    public static String labelOf(float index) {
        for (FreshFoodGrade grade : values()) {
            if (index >= grade.threshold) {
                return grade.label;
            }
        }
        return VERY_LOW.label;
    }
}
