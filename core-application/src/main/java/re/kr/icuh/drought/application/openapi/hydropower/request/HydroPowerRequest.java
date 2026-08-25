package re.kr.icuh.drought.application.openapi.hydropower.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record HydroPowerRequest(
        @NotBlank(message = "연도는 필수 값입니다.")
        @Pattern(regexp = "^20[0-9][0-9]$", message = "연도는 2000~2099 사이의 4자리 숫자여야 합니다.")
        String year,

        @NotBlank(message = "월은 필수 값입니다.")
        @Pattern(regexp = "^([1-9]|1[0-2])$", message = "월은 1~12 사이의 값이어야 합니다.")
        String month,

        @NotBlank(message = "댐 이름은 필수 값입니다.")
        String damName
) {
}
