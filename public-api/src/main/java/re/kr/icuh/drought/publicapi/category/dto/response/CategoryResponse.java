package re.kr.icuh.drought.publicapi.category.dto.response;

import java.util.List;

public record CategoryResponse(
    List<DocumentTypesResponse> documentTypesResponse,
    List<SubjectDomainsResponse> subjectDomainsResponses
) {
    public static CategoryResponse of(List<DocumentTypesResponse> documentTypesResponses, List<SubjectDomainsResponse> subjectDomainsResponses) {
        return new CategoryResponse(documentTypesResponses, subjectDomainsResponses);
    }
}
