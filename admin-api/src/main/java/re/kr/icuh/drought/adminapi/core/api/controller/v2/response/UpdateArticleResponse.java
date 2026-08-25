package re.kr.icuh.drought.adminapi.core.api.controller.v2.response;

import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.DocumentTypeResponse;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.SubjectDomainResponse;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.domain.article.UpdateArticleRequest;
import re.kr.icuh.drought.persistence.article.entity.DocumentType;
import re.kr.icuh.drought.persistence.article.entity.SubjectDomain;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateArticleResponse(
        Long id,
        String title,
        String description,
        String author,
        String authorOrganization,
        String department,
        ArticleStatus status,
        LocalDateTime updatedAt,
        DocumentTypeResponse documentType,
        SubjectDomainResponse subjectDomain,
        String source,
        String originUrl,
        List<NewFileResponse> files
) {
    public record NewFileResponse(
            String originalFileName,
            String storedFileName,
            String filePath,
            Long fileSize,
            String extension
    ) {
        public static NewFileResponse of(UpdateArticleRequest.NewFileRequest newFileRequest) {
            return new NewFileResponse(
                    newFileRequest.originalFileName(),
                    newFileRequest.storedFileName(),
                    newFileRequest.filePath(),
                    newFileRequest.fileSize(),
                    newFileRequest.extension()
            );
        }
    }

    public static UpdateArticleResponse of(Long id, ArticleStatus status, UpdateArticleRequest updateArticleRequest, LocalDateTime updatedAt, DocumentType documentType, SubjectDomain subjectDomain) {
        return new UpdateArticleResponse(
                id,
                updateArticleRequest.title(),
                updateArticleRequest.description(),
                updateArticleRequest.author(),
                updateArticleRequest.authorOrganization(),
                updateArticleRequest.department(),
                status,
                updatedAt,
                DocumentTypeResponse.fromEntity(documentType),
                SubjectDomainResponse.fromEntity(subjectDomain),
                updateArticleRequest.source(),
                "http://localhost:5173/detail/" + id,
                updateArticleRequest.newFiles().stream().map(NewFileResponse::of).toList()
        );
    }
}
