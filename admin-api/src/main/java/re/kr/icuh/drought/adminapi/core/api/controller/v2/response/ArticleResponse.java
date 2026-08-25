package re.kr.icuh.drought.adminapi.core.api.controller.v2.response;

import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.DocumentTypeResponse;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.FileResponse;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.SubjectDomainResponse;
import re.kr.icuh.drought.adminapi.core.domain.Article;
import re.kr.icuh.drought.domain.article.ArticleStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ArticleResponse(
        Long id,
        String title,
        String description,
        String author,
        String authorOrganization,
        String department,
        ArticleStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer views,
        DocumentTypeResponse documentType,
        SubjectDomainResponse subjectDomain,
        List<FileResponse> files
) {
    public static ArticleResponse fromEntity(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getDescription(),
                article.getAuthor(),
                article.getAuthorOrganization(),
                article.getDepartment(),
                article.getStatus(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                article.getViews(),
                DocumentTypeResponse.fromEntity(article.getDocumentType()),
                SubjectDomainResponse.fromEntity(article.getSubjectDomain()),
                article.getFiles().stream().map(FileResponse::fromEntity).toList()
        );
    }
}
