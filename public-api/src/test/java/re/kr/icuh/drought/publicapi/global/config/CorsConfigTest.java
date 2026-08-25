package re.kr.icuh.drought.publicapi.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    @DisplayName("콤마로 구분된 다중 오리진을 개별 오리진으로 분리한다 (공백 제거)")
    void 다중_오리진을_분리한다() {
        // given
        String raw = "https://a.com, https://b.com,https://c.com";

        // when
        String[] origins = CorsConfig.parseAllowedOrigins(raw);

        // then
        assertThat(origins).containsExactly("https://a.com", "https://b.com", "https://c.com");
    }

    @Test
    @DisplayName("단일 오리진은 1개 원소로 반환한다")
    void 단일_오리진을_반환한다() {
        // when
        String[] origins = CorsConfig.parseAllowedOrigins("https://a.com");

        // then
        assertThat(origins).containsExactly("https://a.com");
    }
}
