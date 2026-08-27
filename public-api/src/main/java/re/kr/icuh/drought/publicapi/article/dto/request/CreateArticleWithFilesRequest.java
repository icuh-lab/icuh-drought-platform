package re.kr.icuh.drought.publicapi.article.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import re.kr.icuh.drought.domain.article.ArticleSource;
import re.kr.icuh.drought.publicapi.file.dto.request.CompletedFileUpload;

import java.util.List;

public record CreateArticleWithFilesRequest(
        @NotNull String title,
        @NotNull String description,
        @NotNull String author,
        @NotNull String authorOrganization,
        @NotNull String department,
        @NotNull String tempPassword,
        @NotNull String documentTypeCode,
        @NotNull String subjectDomainCode,
        @NotNull @Pattern(regexp = ArticleSource.PATTERN, message = ArticleSource.MESSAGE) String source,
        @NotNull List<CompletedFileUpload> completedFiles
) {}
