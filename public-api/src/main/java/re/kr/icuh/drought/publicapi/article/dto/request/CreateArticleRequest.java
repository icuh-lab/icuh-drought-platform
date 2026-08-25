package re.kr.icuh.drought.publicapi.article.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateArticleRequest(
        @NotNull String title,
        @NotNull String description,
        @NotNull String author,
        @NotNull String authorOrganization,
        @NotNull String department,
        @NotNull String tempPassword,
        @NotNull Long documentTypeId,
        @NotNull Long subjectDomainId,
        @NotNull String source
) {}
