package re.kr.icuh.drought.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @DisplayName("각 ErrorCode의 code 문자열은 enum 상수 이름과 일치한다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(ErrorCode.class)
    void codeMatchesEnumName(ErrorCode errorCode) {
        assertThat(errorCode.getCode()).isEqualTo(errorCode.name());
    }
}
