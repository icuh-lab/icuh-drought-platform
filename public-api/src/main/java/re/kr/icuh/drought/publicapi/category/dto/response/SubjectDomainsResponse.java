package re.kr.icuh.drought.publicapi.category.dto.response;

import re.kr.icuh.drought.persistence.article.entity.SubjectDomain;

public record SubjectDomainsResponse(
    String code,
    String name,
    String enName
) {
    public static SubjectDomainsResponse from(SubjectDomain subjectDomain) {
        return new SubjectDomainsResponse(
                subjectDomain.getCode(),
                subjectDomain.getName(),
                subjectDomain.getEnName()
        );
    }
}
