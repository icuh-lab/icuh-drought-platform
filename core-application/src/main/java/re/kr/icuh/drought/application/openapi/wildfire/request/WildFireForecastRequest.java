package re.kr.icuh.drought.application.openapi.wildfire.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import re.kr.icuh.drought.application.openapi.support.MonthParam;

/**
 * 산불위험 예보 조회 요청.
 * 날짜를 지정하면 그 날짜부터, 생략하면 오늘부터 3일치를 조회한다.
 * 시간대는 어느 쪽이든 현재 시각이 속한 3시간 슬롯을 쓴다.
 */
public record WildFireForecastRequest(
        @Pattern(regexp = "^20[0-9][0-9]$", message = "연도는 2000~2099 사이의 4자리 숫자여야 합니다.")
        String year,

        @Pattern(regexp = MonthParam.PATTERN, message = "월은 1~12 사이의 값이어야 합니다.")
        String month,

        @Pattern(regexp = "^(0?[1-9]|[12][0-9]|3[01])$", message = "일은 1~31 사이의 값이어야 합니다.")
        String day
) {

    public WildFireForecastRequest {
        year = emptyToNull(year);
        month = MonthParam.normalize(emptyToNull(month));
        day = emptyToNull(day);
    }

    @AssertTrue(message = "연·월·일은 모두 지정하거나 모두 생략해야 합니다.")
    public boolean isDateComplete() {
        return hasDate() || (year == null && month == null && day == null);
    }

    public boolean hasDate() {
        return year != null && month != null && day != null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
