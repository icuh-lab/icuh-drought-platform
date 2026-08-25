package re.kr.icuh.drought.publicapi.article.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import re.kr.icuh.drought.publicapi.article.domain.Article;
import re.kr.icuh.drought.publicapi.article.domain.ArticleStatus;
import re.kr.icuh.drought.publicapi.article.dto.request.CreateArticleWithFilesRequest;
import re.kr.icuh.drought.publicapi.article.dto.request.ModifyArticleStatusRequest;
import re.kr.icuh.drought.publicapi.article.infra.ArticleRepository;
import re.kr.icuh.drought.persistence.article.entity.DocumentType;
import re.kr.icuh.drought.persistence.article.entity.SubjectDomain;
import re.kr.icuh.drought.persistence.article.repository.DocumentTypeRepository;
import re.kr.icuh.drought.persistence.article.repository.SubjectDomainRepository;
import re.kr.icuh.drought.publicapi.file.infra.FileRepository;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private DocumentTypeRepository documentTypeRepository;

    @Mock
    private SubjectDomainRepository subjectDomainRepository;

    @InjectMocks
    private ArticleService articleService;

    @Test
    @DisplayName("문서 성격 검증 실패 시 실제 예외(DOCUMENT_TYPE_NOT_FOUND)가 전파되어야 하며 FILE_SIZE_EXCEEDED로 뭉개지지 않는다")
    void createArticleWithFiles_documentTypeNotFound_propagatesRealException() {
        // given
        when(documentTypeRepository.findByCode(anyString())).thenReturn(Optional.empty());

        CreateArticleWithFilesRequest request = new CreateArticleWithFilesRequest(
                null,            // title
                null,            // description
                null,            // author
                null,            // authorOrganization
                null,            // department
                null,            // tempPassword
                "INVALID_CODE",  // documentTypeCode
                null,            // subjectDomainCode
                null,            // source
                null             // completedFiles
        );

        // when & then
        assertThatThrownBy(() -> articleService.createArticleWithFiles(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_TYPE_NOT_FOUND);

        BusinessException thrown = (BusinessException) org.assertj.core.api.Assertions.catchThrowable(
                () -> articleService.createArticleWithFiles(request));
        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.DOCUMENT_TYPE_NOT_FOUND);
        assertThat(thrown.getErrorCode()).isNotEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("modifyArticleStatus는 PENDING 게시글을 REJECTED로 전이한다")
    void modifyArticleStatus_PENDING을_REJECTED로_전이한다() {
        // given
        DocumentType documentType = new DocumentType(1L, "문서성격", "docType", "DOC", null, null, "ACTIVE");
        SubjectDomain subjectDomain = new SubjectDomain(1L, "주제영역", "subject", "SUB", null, null, "ACTIVE");
        Article article = Article.builder()
                .title("제목")
                .description("내용")
                .author("작성자")
                .authorOrganization("작성기관")
                .department("부서")
                .tempPassword("1234")
                .views(0)
                .status(ArticleStatus.PENDING)
                .documentType(documentType)
                .subjectDomain(subjectDomain)
                .source("출처")
                .isDeleted(false)
                .deletedAt(null)
                .build();
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));

        // when
        articleService.modifyArticleStatus(1L, new ModifyArticleStatusRequest("1234", "부적절한 문서"));

        // then
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.REJECTED);
    }
}
