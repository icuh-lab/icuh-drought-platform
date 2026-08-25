package re.kr.icuh.drought.adminapi.core.api.controller.v2.response;

import re.kr.icuh.drought.adminapi.core.domain.FileEntity;

public record FileResponse(
        Long id,
        String originalFileName,
        String extension,
        Long fileSize,
        String filePath,
        String downloadUrl
) {
    public static FileResponse fromEntity(FileEntity fileEntity) {
        return new FileResponse(
                fileEntity.getId(),
                fileEntity.getOriginalFilename(),
                fileEntity.getExtension(),
                fileEntity.getFileSize(),
                fileEntity.getFilePath(),
                "/api/v1/multipart-upload/files" + fileEntity.getId() + "/download"
        );
    }
}
