package re.kr.icuh.drought.publicapi.file.dto.request;

import jakarta.validation.constraints.NotNull;

public record CompletedFileUpload(
        @NotNull String originalFileName,
        @NotNull String storedFileName,
        @NotNull String filePath,
        @NotNull Long fileSize,
        @NotNull String extension
) {}
