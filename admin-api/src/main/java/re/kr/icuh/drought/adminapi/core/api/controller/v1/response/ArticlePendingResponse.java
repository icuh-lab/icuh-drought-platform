package re.kr.icuh.drought.adminapi.core.api.controller.v1.response;

import re.kr.icuh.drought.persistence.article.entity.Article;
import re.kr.icuh.drought.persistence.article.entity.FileEntity;
import re.kr.icuh.drought.domain.article.ArticleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record ArticlePendingResponse(
        Long id,
        String title,
        String description,
        String author,
        String authorOrganization,
        String department,
        ArticleStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer views,
        DocumentTypeResponse classification,
        SubjectDomainResponse serviceType,
        List<FileResponseWithDownloadInfo> files
) {
    public static ArticlePendingResponse fromEntity(Article article) {
        return new ArticlePendingResponse(
                article.getId(),
                article.getTitle(),
                article.getDescription(),
                article.getAuthor(),
                article.getAuthorOrganization(),
                article.getDepartment(),
                article.getStatus(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                article.getViews(),
                DocumentTypeResponse.fromEntity(article.getDocumentType()),
                SubjectDomainResponse.fromEntity(article.getSubjectDomain()),
                article.getFiles().stream()
                        .map(FileResponseWithDownloadInfo::fromEntity)
                        .collect(Collectors.toList())
        );
    }
}

// 파일 다운로드 정보가 포함된 FileResponse 클래스
record FileResponseWithDownloadInfo(
        Long id,
        String originalFilename,
        String extension,
        Long fileSize,
        String filePath,
        String downloadUrl
) {
    public static FileResponseWithDownloadInfo fromEntity(FileEntity file) {
        return new FileResponseWithDownloadInfo(
                file.getId(),
                file.getOriginalFilename(),
                file.getExtension(),
                file.getFileSize(),
                file.getFilePath(),
                "/api/v1/multipart-upload/files/" + file.getId() + "/download"
        );
    }
}
