package re.kr.icuh.drought.adminapi.core.api.controller.v1.response;

import re.kr.icuh.drought.adminapi.core.domain.ArticleEditRequest;
import re.kr.icuh.drought.adminapi.core.domain.ArticleStatus;

import java.time.LocalDateTime;

public record ArticleEditRequestListResponse(
        Long id,
        String title,
        String authorOrganization,
        String department,
        String author,
        ArticleStatus status,
        LocalDateTime createdAt
) {
    public static ArticleEditRequestListResponse fromEntity(ArticleEditRequest articleEditRequest) {
        return new ArticleEditRequestListResponse(
                articleEditRequest.getId(),
                articleEditRequest.getTitle(),
                articleEditRequest.getAuthorOrganization(),
                articleEditRequest.getDepartment(),
                articleEditRequest.getAuthor(),
                articleEditRequest.getStatus(),
                articleEditRequest.getCreatedAt()
        );
    }
}
