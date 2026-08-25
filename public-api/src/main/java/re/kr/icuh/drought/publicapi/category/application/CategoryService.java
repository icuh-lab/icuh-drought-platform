package re.kr.icuh.drought.publicapi.category.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import re.kr.icuh.drought.publicapi.category.dto.response.CategoryResponse;
import re.kr.icuh.drought.publicapi.category.dto.response.DocumentTypesResponse;
import re.kr.icuh.drought.publicapi.category.dto.response.SubjectDomainsResponse;
import re.kr.icuh.drought.persistence.article.repository.DocumentTypeRepository;
import re.kr.icuh.drought.persistence.article.repository.SubjectDomainRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final DocumentTypeRepository documentTypeRepository;
    private final SubjectDomainRepository subjectDomainRepository;

    public CategoryResponse getCategories() {
        List<DocumentTypesResponse> documentTypesResponses = documentTypeRepository.findByStatus("ACTIVE")
                .stream()
                .map(DocumentTypesResponse::from)
                .toList();

        List<SubjectDomainsResponse> subjectDomainsResponses = subjectDomainRepository.findByStatus("ACTIVE")
                .stream()
                .map(SubjectDomainsResponse::from)
                .toList();

        return new CategoryResponse(documentTypesResponses, subjectDomainsResponses);
    }
}
