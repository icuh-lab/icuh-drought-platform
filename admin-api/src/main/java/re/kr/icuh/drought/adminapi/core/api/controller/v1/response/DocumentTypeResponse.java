package re.kr.icuh.drought.adminapi.core.api.controller.v1.response;

import re.kr.icuh.drought.persistence.article.entity.DocumentType;

public record DocumentTypeResponse(
        Long id,
        String name
) {
    public static DocumentTypeResponse fromEntity(DocumentType documentType) {
        return new DocumentTypeResponse(
                documentType.getId(),
                documentType.getName()
        );
    }
}
