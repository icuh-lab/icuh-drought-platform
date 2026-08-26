package re.kr.icuh.drought.persistence.article.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;
import re.kr.icuh.drought.domain.article.ArticleStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code Article.changeStatus}가 전이 판정을 {@link ArticleStatus}에 위임하는지 확인한다.
 * 전이표 자체의 검증은 core-domain의 {@code ArticleStatusTransitionTest}에 있다.
 */
class ArticleChangeStatusTest {

    private Article articleWith(ArticleStatus status) {
        return Article.builder()
                .title("제목")
                .description("내용")
                .author("작성자")
                .authorOrganization("작성기관")
                .department("부서")
                .tempPassword("password")
                .views(0)
                .status(status)
                .source("출처")
                .isDeleted(false)
                .build();
    }

    @ParameterizedTest(name = "{0} -> APPROVED")
    @EnumSource(ArticleStatus.class)
    @DisplayName("승인 경로(4·6·8번)는 소스 상태를 가리지 않고 APPROVED로 전이한다 - APPROVED 자기 전이 포함")
    void 모든_상태에서_APPROVED로_전이한다(ArticleStatus source) {
        Article article = articleWith(source);

        article.changeStatus(ArticleStatus.APPROVED);

        assertThat(article.getStatus()).isEqualTo(ArticleStatus.APPROVED);
    }

    @Test
    @DisplayName("수정 승인(9번)은 APPROVED에서 UPDATED_APPROVED로 전이한다")
    void APPROVED에서_UPDATED_APPROVED로_전이한다() {
        Article article = articleWith(ArticleStatus.APPROVED);

        article.changeStatus(ArticleStatus.UPDATED_APPROVED);

        assertThat(article.getStatus()).isEqualTo(ArticleStatus.UPDATED_APPROVED);
    }

    @Test
    @DisplayName("삭제 승인(5번)은 DELETED_PENDING에서 DELETED로 전이한다")
    void DELETED_PENDING에서_DELETED로_전이한다() {
        Article article = articleWith(ArticleStatus.DELETED_PENDING);

        article.changeStatus(ArticleStatus.DELETED);

        assertThat(article.getStatus()).isEqualTo(ArticleStatus.DELETED);
    }

    @Test
    @DisplayName("도달 불가능한 전이(DELETED -> PENDING)는 INVALID_INPUT 예외를 던지고 상태를 바꾸지 않는다")
    void 도달_불가능한_전이는_예외를_던진다() {
        Article article = articleWith(ArticleStatus.DELETED);

        assertThatThrownBy(() -> article.changeStatus(ArticleStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThat(article.getStatus()).isEqualTo(ArticleStatus.DELETED);
    }
}
