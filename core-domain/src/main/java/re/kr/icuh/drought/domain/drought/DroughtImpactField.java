package re.kr.icuh.drought.domain.drought;

public enum DroughtImpactField {
    A1("물 공급"),
    A2("농업"),
    A3("축산업"),
    A4("수산업"),
    A5("산업"),
    A6("환경"),
    A7("사회경제"),
    A8("기타");

    private final String displayName;

    DroughtImpactField(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static DroughtImpactField fromCode(String code) {
        for (DroughtImpactField field : values()) {
            if (field.name().equals(code)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unknown drought impact code: " + code);
    }
}
