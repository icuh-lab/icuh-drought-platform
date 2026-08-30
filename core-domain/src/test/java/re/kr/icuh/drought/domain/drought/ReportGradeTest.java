package re.kr.icuh.drought.domain.drought;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportGradeTest {

    @Test
    @DisplayName("등급 순서는 관심 < 주의 < 경계 < 심각이다")
    void ordinalOrderMatchesSeverity() {
        assertThat(ReportGrade.관심).isLessThan(ReportGrade.주의);
        assertThat(ReportGrade.주의).isLessThan(ReportGrade.경계);
        assertThat(ReportGrade.경계).isLessThan(ReportGrade.심각);
    }

    @Test
    @DisplayName("여러 등급 중 최댓값은 Comparable 순서로 구해진다")
    void maxPicksTheMostSevereGrade() {
        assertThat(Collections.max(List.of(ReportGrade.주의, ReportGrade.심각, ReportGrade.관심)))
                .isEqualTo(ReportGrade.심각);
    }
}
