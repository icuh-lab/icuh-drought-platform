package re.kr.icuh.drought.publicapi.category.dto.response;

import re.kr.icuh.drought.persistence.article.entity.DocumentType;

public record DocumentTypesResponse(
    String code,
    String name,
    String enName
) {
    public static DocumentTypesResponse from(DocumentType documentType) {
        return new DocumentTypesResponse(
                documentType.getCode(),
                documentType.getName(),
                documentType.getEnName()
        );
    }
}
