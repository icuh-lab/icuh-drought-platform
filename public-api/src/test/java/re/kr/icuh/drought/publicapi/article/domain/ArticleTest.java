package re.kr.icuh.drought.publicapi.article.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleTest {

    private Article createArticle() {
        return Article.builder()
                .title("제목")
                .description("내용")
                .author("작성자")
                .authorOrganization("작성기관")
                .department("부서")
                .tempPassword("password")
                .views(0)
                .status(ArticleStatus.PENDING)
                .documentType(null)
                .subjectDomain(null)
                .source("출처")
                .isDeleted(false)
                .deletedAt(null)
                .build();
    }

    @Test
    @DisplayName("delete() 호출 시 status가 DELETED_PENDING으로 변경되고 isDeleted와 deletedAt이 함께 세팅된다")
    void delete_소프트삭제_표식이_함께_세팅된다() {
        // given
        Article article = createArticle();

        // when
        article.delete();

        // then
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.DELETED_PENDING);
        assertThat(article.getIsDeleted()).isTrue();
        assertThat(article.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("reject() 호출 시 PENDING 상태가 REJECTED로 변경된다")
    void reject_PENDING을_REJECTED로_전이한다() {
        // given
        Article article = createArticle();

        // when
        article.reject();

        // then
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.REJECTED);
    }

    @Test
    @DisplayName("reject()는 PENDING이 아닌 상태에서 호출되면 INVALID_INPUT 예외를 던진다")
    void reject_PENDING이_아니면_예외를_던진다() {
        // given
        Article article = Article.builder()
                .title("제목")
                .description("내용")
                .author("작성자")
                .authorOrganization("작성기관")
                .department("부서")
                .tempPassword("password")
                .views(0)
                .status(ArticleStatus.APPROVED)
                .documentType(null)
                .subjectDomain(null)
                .source("출처")
                .isDeleted(false)
                .deletedAt(null)
                .build();

        // when & then
        assertThatThrownBy(article::reject)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("sha256Encode()는 ASCII 문자열을 기존 구현과 동일한 SHA-256 hex로 인코딩한다")
    void sha256Encode_ASCII_문자열을_해싱한다() {
        // given
        Article article = createArticle();

        // when
        String hex = article.sha256Encode("test1234");

        // then
        assertThat(hex).isEqualTo("937e8d5fbb48bd4949536cd65b8d35c426b80d2f830c5c308e2cdec422ae2244");
    }

    @Test
    @DisplayName("sha256Encode()는 한글(UTF-8 멀티바이트) 문자열을 기존 구현과 동일한 SHA-256 hex로 인코딩한다")
    void sha256Encode_한글_문자열을_해싱한다() {
        // given
        Article article = createArticle();

        // when
        String hex = article.sha256Encode("비밀번호1234");

        // then
        assertThat(hex).isEqualTo("a0191747a9c89b6b08303dc7f1497b4d34ebd44e978045eb0d5da4c04f04705f");
    }

    @Test
    @DisplayName("sha256Encode()는 빈 문자열을 기존 구현과 동일한 SHA-256 hex로 인코딩한다")
    void sha256Encode_빈_문자열을_해싱한다() {
        // given
        Article article = createArticle();

        // when
        String hex = article.sha256Encode("");

        // then
        assertThat(hex).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
