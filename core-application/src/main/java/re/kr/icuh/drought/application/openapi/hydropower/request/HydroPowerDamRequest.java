package re.kr.icuh.drought.application.openapi.hydropower.request;

import jakarta.validation.constraints.NotBlank;

public record HydroPowerDamRequest(
        @NotBlank(message = "댐 이름은 필수 값입니다.")
        String damName
) {
}
