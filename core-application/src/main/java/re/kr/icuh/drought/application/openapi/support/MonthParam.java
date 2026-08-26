package re.kr.icuh.drought.application.openapi.support;

/**
 * 조회 파라미터로 들어온 월 표기를 다룬다.
 * 저장된 월은 "7"처럼 0을 채우지 않은 문자열이라, 요청의 "07"을 그대로 비교하면 조회가 빗나간다.
 */
public final class MonthParam {

    /** "7"과 "07"을 모두 허용한다. */
    public static final String PATTERN = "^(0?[1-9]|1[0-2])$";

    private MonthParam() {
    }

    /** "07"처럼 0을 채운 월을 "7"로 맞춘다. */
    public static String normalize(String month) {
        if (month != null && month.length() == 2 && month.charAt(0) == '0') {
            return month.substring(1);
        }
        return month;
    }
}
