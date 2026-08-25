package re.kr.icuh.drought.adminapi.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.persistence.article.entity.Article;
import re.kr.icuh.drought.persistence.article.entity.FileEntity;

import static org.assertj.core.api.Assertions.assertThat;

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
                .rejectReason(null)
                .build();
    }

    private FileEntity createFileWithoutArticle() {
        return FileEntity.builder()
                .originalFilename("원본파일명.pdf")
                .storedFilename("stored-file-name.pdf")
                .filePath("/path/to/file")
                .extension("pdf")
                .fileSize(1024L)
                .build();
    }

    @Test
    @DisplayName("addFile() 호출 시 files 목록에 추가되고 file의 article 연관관계도 함께 세팅된다")
    void addFile_양방향_연관관계가_세팅된다() {
        // given
        Article article = createArticle();
        FileEntity file = createFileWithoutArticle();

        // when
        article.addFile(file);

        // then
        assertThat(article.getFiles()).containsExactly(file);
        assertThat(file.getArticle()).isSameAs(article);
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
