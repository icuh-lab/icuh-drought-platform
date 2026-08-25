package re.kr.icuh.drought.persistence.openapi.freshfood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FreshFoodGradeTest {

    @ParameterizedTest(name = "지수 {0} -> 등급 {1}")
    @CsvSource({
            "120.0, veryHigh",
            "115.1, veryHigh",   // 경계: float 임계값이라 정확히 일치(이전 double 비교에선 어긋났음)
            "115.0, high",
            "105.1, high",
            "95.1,  normal",
            "85.1,  low",        // 경계: 이제 low로 정확히 분류
            "85.0,  veryLow",
            "0.0,   veryLow"
    })
    @DisplayName("지수 임계값에 따라 등급 라벨을 매긴다 (경계 포함)")
    void mapsIndexToGradeLabel(float index, String expected) {
        assertThat(FreshFoodGrade.labelOf(index)).isEqualTo(expected);
    }
}
