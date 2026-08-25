package re.kr.icuh.drought.adminapi.core.api.controller.v1.response;

import re.kr.icuh.drought.adminapi.core.domain.FileEntity;

import java.time.LocalDateTime;

public record FileResponse(
        Long id,
        String originalFilename,
        String filePath,
        Long fileSize,
        String extension,
        LocalDateTime createdAt
) {
    public static FileResponse fromEntity(FileEntity file) {
        return new FileResponse(
                file.getId(),
                file.getOriginalFilename(),
                file.getFilePath(),
                file.getFileSize(),
                file.getExtension(),
                file.getCreatedAt()
        );
    }
}
