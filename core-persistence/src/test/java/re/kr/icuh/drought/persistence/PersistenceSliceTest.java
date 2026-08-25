package re.kr.icuh.drought.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.jdbc.Sql;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.domain.article.UpdateArticleRequest;
import re.kr.icuh.drought.persistence.article.entity.Article;
import re.kr.icuh.drought.persistence.article.entity.DocumentType;
import re.kr.icuh.drought.persistence.article.repository.DocumentTypeRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@EntityScan(basePackages = "re.kr.icuh.drought.persistence")
@EnableJpaRepositories(basePackages = "re.kr.icuh.drought.persistence")
class PersistenceSliceTest {

    @Autowired
    private DocumentTypeRepository documentTypeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void articleReferenceRepositoriesAreWired() {
        DocumentType saved = documentTypeRepository.save(new DocumentType(
                null,
                "보고서",
                "Report",
                "REPORT",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "ACTIVE"
        ));

        assertThat(documentTypeRepository.findByCode("REPORT"))
                .isPresent()
                .get()
                .extracting(DocumentType::getId)
                .isEqualTo(saved.getId());
    }

    /**
     * public-api가 {@code pending_update}에 적재한 수정 요청을 admin-api가 그대로 읽어 가는 것이
     * 두 앱 사이의 암묵적 계약이다. 컨버터가 실제로 배선되고 JSON이 왕복하는지를 여기서 증명한다.
     * <p>
     * 이 슬라이스의 H2 스키마는 Hibernate가 생성하므로 {@code pending_update}가 varchar(255)로 만들어진다.
     * 운영 MySQL 컬럼은 그보다 크다(admin이 실제 payload를 쓰고 있다). 엔티티 매핑을 바꾸는 대신
     * 테스트 스키마만 넓힌다 — 운영 스키마는 외부에서 관리되고 {@code ddl-auto: none}이다.
     */
    @Test
    @DisplayName("pending_update 컬럼은 중첩된 newFiles까지 포함해 JSON 왕복한다")
    @Sql(statements = "ALTER TABLE articles ALTER COLUMN pending_update SET DATA TYPE VARCHAR(4000)")
    void pendingUpdateRoundTripsThroughJsonColumn() {
        // given
        UpdateArticleRequest pendingUpdate = new UpdateArticleRequest(
                "수정된 제목",
                "수정된 본문",
                "홍길동",
                "인프라재난관리진흥원",
                "연구개발부",
                "temp-password",
                "REPORT",
                "DROUGHT",
                "국가가뭄정보포털",
                List.of(
                        new UpdateArticleRequest.NewFileRequest(
                                "원본보고서.pdf", "stored-uuid.pdf", "upload/stored-uuid.pdf", 2048L, "pdf"),
                        new UpdateArticleRequest.NewFileRequest(
                                "부록.xlsx", "stored-uuid-2.xlsx", "upload/stored-uuid-2.xlsx", 4096L, "xlsx")
                )
        );

        Article article = Article.builder()
                .title("제목")
                .description("본문")
                .author("작성자")
                .authorOrganization("작성기관")
                .department("부서")
                .tempPassword("password")
                .views(0)
                .status(ArticleStatus.APPROVED)
                .source("출처")
                .isDeleted(false)
                .build();
        article.updateContent(pendingUpdate);

        Long id = entityManager.persistAndGetId(article, Long.class);

        // when: 1차 캐시가 아니라 DB에서 다시 읽도록 영속성 컨텍스트를 비운다
        entityManager.flush();
        entityManager.clear();

        Article reloaded = entityManager.find(Article.class, id);

        // then
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getPendingUpdate())
                .usingRecursiveComparison()
                .isEqualTo(pendingUpdate);
        assertThat(reloaded.getPendingUpdate().newFiles())
                .extracting(
                        UpdateArticleRequest.NewFileRequest::originalFileName,
                        UpdateArticleRequest.NewFileRequest::storedFileName,
                        UpdateArticleRequest.NewFileRequest::filePath,
                        UpdateArticleRequest.NewFileRequest::fileSize,
                        UpdateArticleRequest.NewFileRequest::extension)
                .containsExactly(
                        tuple("원본보고서.pdf", "stored-uuid.pdf", "upload/stored-uuid.pdf", 2048L, "pdf"),
                        tuple("부록.xlsx", "stored-uuid-2.xlsx", "upload/stored-uuid-2.xlsx", 4096L, "xlsx"));
    }

    @SpringBootConfiguration
    static class TestApplication {

        /**
         * {@code @DataJpaTest}는 Jackson을 자동 구성하지 않는다. JSON 컨버터가 {@code ObjectMapper}를
         * 생성자 주입받으므로 빈이 없으면 컨텍스트 로딩부터 실패한다. (운영에서는 web starter가 제공한다.)
         */
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
