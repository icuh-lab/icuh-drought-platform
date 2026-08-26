package re.kr.icuh.drought.publicapi.article.dto.response;

import re.kr.icuh.drought.persistence.article.entity.Article;
import re.kr.icuh.drought.publicapi.category.dto.response.DocumentTypeResponse;
import re.kr.icuh.drought.publicapi.category.dto.response.SubjectDomainResponse;
import re.kr.icuh.drought.publicapi.file.dto.response.FileResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record ArticleDetailResponse(
        Long id,
        String title,
        String description,
        String author,
        String authorOrganization,
        String department,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer views,
        DocumentTypeResponse classification,
        SubjectDomainResponse serviceType,
        String source,
        String sourceUrl,
        Integer sourceArticleCount,
        List<String> regionMentions,
        List<String> keywords,
        String autoSummaryNotice,
        List<FileResponse> files
) {
    public static ArticleDetailResponse of(Article article) {
        return new ArticleDetailResponse(
                article.getId(),
                article.getTitle(),
                article.getDescription(),
                article.getAuthor(),
                article.getAuthorOrganization(),
                article.getDepartment(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                article.getViews(),
                DocumentTypeResponse.fromEntity(article.getDocumentType()),
                SubjectDomainResponse.fromEntity(article.getSubjectDomain()),
                article.getSource(),
                null,
                0,
                List.of(),
                List.of(),
                null,
                article.getFiles().stream()
                        .map(FileResponse::fromEntity)
                        .collect(Collectors.toList())
        );
    }
}
