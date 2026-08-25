package re.kr.icuh.drought.adminapi.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
