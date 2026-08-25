package re.kr.icuh.drought.publicapi.article.dto.request;

import jakarta.validation.constraints.NotNull;
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
        @NotNull String source,
        @NotNull List<CompletedFileUpload> completedFiles
) {}
