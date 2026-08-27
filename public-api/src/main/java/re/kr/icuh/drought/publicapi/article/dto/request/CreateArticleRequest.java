package re.kr.icuh.drought.publicapi.article.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import re.kr.icuh.drought.domain.article.ArticleSource;

public record CreateArticleRequest(
        @NotNull String title,
        @NotNull String description,
        @NotNull String author,
        @NotNull String authorOrganization,
        @NotNull String department,
        @NotNull String tempPassword,
        @NotNull Long documentTypeId,
        @NotNull Long subjectDomainId,
        @NotNull @Pattern(regexp = ArticleSource.PATTERN, message = ArticleSource.MESSAGE) String source
) {}
