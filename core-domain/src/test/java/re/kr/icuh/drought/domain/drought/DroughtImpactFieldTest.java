package re.kr.icuh.drought.domain.drought;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DroughtImpactFieldTest {

    @Test
    @DisplayName("A1~A8 코드는 실제 impact_field 테이블의 한글명과 정확히 일치한다")
    void displayNameMatchesRealData() {
        assertThat(DroughtImpactField.A1.displayName()).isEqualTo("물 공급");
        assertThat(DroughtImpactField.A2.displayName()).isEqualTo("농업");
        assertThat(DroughtImpactField.A3.displayName()).isEqualTo("축산업");
        assertThat(DroughtImpactField.A4.displayName()).isEqualTo("수산업");
        assertThat(DroughtImpactField.A5.displayName()).isEqualTo("산업");
        assertThat(DroughtImpactField.A6.displayName()).isEqualTo("환경");
        assertThat(DroughtImpactField.A7.displayName()).isEqualTo("사회경제");
        assertThat(DroughtImpactField.A8.displayName()).isEqualTo("기타");
    }

    @Test
    @DisplayName("fromCode는 코드 문자열로 enum 상수를 찾는다")
    void fromCodeResolvesByName() {
        assertThat(DroughtImpactField.fromCode("A3")).isEqualTo(DroughtImpactField.A3);
    }

    @Test
    @DisplayName("모르는 코드는 IllegalArgumentException을 던진다")
    void fromCodeRejectsUnknownCode() {
        assertThatThrownBy(() -> DroughtImpactField.fromCode("Z9"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
