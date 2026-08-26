package re.kr.icuh.drought.application.openapi.hydropower.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 연 단위로 월별 시계열을 조회하는 요청.
 * 응답이 해당 연도의 월 배열이므로 월 필터를 받지 않는다.
 */
public record HydroPowerYearlyRequest(
        @NotBlank(message = "연도는 필수 값입니다.")
        @Pattern(regexp = "^20[0-9][0-9]$", message = "연도는 2000~2099 사이의 4자리 숫자여야 합니다.")
        String year,

        @NotBlank(message = "댐 이름은 필수 값입니다.")
        String damName
) {
}
