package re.kr.icuh.drought.adminapi.core.api.controller.v1.response;

import re.kr.icuh.drought.persistence.article.entity.Article;
import re.kr.icuh.drought.adminapi.core.domain.ArticleEditRequest;
import re.kr.icuh.drought.adminapi.core.domain.FileEditRequest;
import re.kr.icuh.drought.domain.article.ArticleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record ArticleUpdatePendingResponse(
        Long id,
        String title,
        String description,
        String author,
        String authorOrganization,
        String department,
        ArticleStatus status,
        LocalDateTime createdAt,
        Integer views,
        DocumentTypeResponse classification,
        SubjectDomainResponse serviceType,
        List<FileResponseWithDownloadInfo> files
) {
    public static ArticleUpdatePendingResponse fromEntity(ArticleEditRequest articleEditRequest) {
        return new ArticleUpdatePendingResponse(
                articleEditRequest.getId(),
                articleEditRequest.getTitle(),
                articleEditRequest.getDescription(),
                articleEditRequest.getAuthor(),
                articleEditRequest.getAuthorOrganization(),
                articleEditRequest.getDepartment(),
                articleEditRequest.getStatus(),
                articleEditRequest.getCreatedAt(),
                articleEditRequest.getViews(),
                DocumentTypeResponse.fromEntity(articleEditRequest.getDocumentType()),
                SubjectDomainResponse.fromEntity(articleEditRequest.getSubjectDomain()),
                articleEditRequest.getFiles().stream()
                        .map(FileEditResponseWithDownloadInfo::fromEntity)
                        .collect(Collectors.toList())

        );
    }

    public static ArticleUpdatePendingResponse fromEntity(Article article) {
        return new ArticleUpdatePendingResponse(
                article.getId(),
                article.getTitle(),
                article.getDescription(),
                article.getAuthor(),
                article.getAuthorOrganization(),
                article.getDepartment(),
                article.getStatus(),
                article.getCreatedAt(),
                article.getViews(),
                DocumentTypeResponse.fromEntity(article.getDocumentType()),
                SubjectDomainResponse.fromEntity(article.getSubjectDomain()),
                article.getFiles().stream()
                        .map(FileResponseWithDownloadInfo::fromEntity)
                        .collect(Collectors.toList())

        );
    }

}

record FileEditResponseWithDownloadInfo(
        Long id,
        String originalFilename,
        String extension,
        Long fileSize,
        String filePath,
        String downloadUrl
) {
    public static FileResponseWithDownloadInfo fromEntity(FileEditRequest file) {
        return new FileResponseWithDownloadInfo(
                file.getId(),
                file.getOriginalFilename(),
                file.getExtension(),
                file.getFileSize(),
                file.getFilePath(),
                "/api/v1/multipart-upload/update/files/" + file.getId() + "/download"
        );
    }
}
