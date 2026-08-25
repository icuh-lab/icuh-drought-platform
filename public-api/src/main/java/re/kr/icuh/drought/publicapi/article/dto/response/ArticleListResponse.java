package re.kr.icuh.drought.publicapi.article.dto.response;

import re.kr.icuh.drought.publicapi.article.domain.Article;

import java.time.LocalDateTime;

public record ArticleListResponse(
        Long id,
        String title,
        String authorOrganization,
        LocalDateTime updatedAt,
        Integer views,
        String documentType,
        String subjectDomain,
        String source
) {
    public static ArticleListResponse fromEntity(Article article) {
        return new ArticleListResponse(
                article.getId(),
                article.getTitle(),
                article.getAuthorOrganization(),
                article.getUpdatedAt(),
                article.getViews(),
                article.getDocumentType().getCode(),
                article.getSubjectDomain().getCode(),
                article.getSource()
        );
    }
}
