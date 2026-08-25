package re.kr.icuh.drought.publicapi.global.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void pageMetadataMatchesSpringPage() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5)
        );

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
    }
}
