package re.kr.icuh.drought.persistence.openapi.freshfood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProvinceTest {

    @Test
    @DisplayName("2026-06 신설된 전남광주통합특별시를 코드로 변환한다")
    void resolvesMergedProvince() {
        assertThat(Province.findCodeByName("전남광주통합특별시")).isEqualTo(53);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "광주광역시, 24",
            "전라남도, 36",
            "전북특별자치도, 52",
            "전국, 99"
    })
    @DisplayName("통합 지역이 생겨도 기존 지역의 코드는 그대로다")
    void keepsExistingCodes(String name, int expected) {
        assertThat(Province.findCodeByName(name)).isEqualTo(expected);
    }

    @Test
    @DisplayName("모르는 지역명은 예외 대신 0을 준다 - 한 행 때문에 그 달 전체가 죽으면 안 된다")
    void fallsBackToZeroForUnknownProvince() {
        assertThat(Province.findCodeByName("대구경북통합특별시")).isZero();
    }

    @Test
    @DisplayName("이름이 없으면 0을 준다")
    void fallsBackToZeroForBlankName() {
        assertThat(Province.findCodeByName(null)).isZero();
        assertThat(Province.findCodeByName("")).isZero();
    }
}
