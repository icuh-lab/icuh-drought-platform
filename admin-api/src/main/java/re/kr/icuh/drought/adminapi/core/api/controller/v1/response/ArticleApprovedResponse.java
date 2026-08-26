package re.kr.icuh.drought.adminapi.core.api.controller.v1.response;

import re.kr.icuh.drought.persistence.article.entity.Article;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.domain.article.FileStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record ArticleApprovedResponse(
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
        DocumentTypeResponse classification,
        SubjectDomainResponse serviceType,
        List<FileResponse> files
) {
    public static ArticleApprovedResponse fromEntity(Article article) {
        return new ArticleApprovedResponse(
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
                article.getFiles().stream()
                        .filter(file -> file.getStatus() == FileStatus.APPROVED)
                        .map(FileResponse::fromEntity)
                        .collect(Collectors.toList())
        );
    }
}
