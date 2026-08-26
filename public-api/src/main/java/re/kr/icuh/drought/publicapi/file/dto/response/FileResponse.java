package re.kr.icuh.drought.publicapi.file.dto.response;

import re.kr.icuh.drought.persistence.article.entity.FileEntity;

public record FileResponse(
        Long id,
        String originalFilename,
        String extension,
        Long fileSize,
        String filePath,
        String downloadUrl
) {
    public static FileResponse fromEntity(FileEntity file) {
        return new FileResponse(
                file.getId(),
                file.getOriginalFilename(),
                file.getExtension(),
                file.getFileSize(),
                file.getFilePath(),
                "/api/v1/multipart-upload/files/" + file.getId() + "/download"
        );
    }
}
