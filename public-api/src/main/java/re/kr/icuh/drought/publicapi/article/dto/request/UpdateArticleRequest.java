package re.kr.icuh.drought.publicapi.article.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateArticleRequest(
        @NotNull String title,
        @NotNull String description,
        @NotNull String author,
        @NotNull String authorOrganization,
        @NotNull String department,
        @NotNull String tempPassword,
        @NotNull String documentTypeCode,
        @NotNull String subjectDomainCode,
        @NotNull String source,
        List<NewFileRequest> newFiles
) {
    public record NewFileRequest(
            String originalFileName,
            String storedFileName,
            String filePath,
            Long fileSize,
            String extension
    ) {}
}

