package re.kr.icuh.drought.adminapi.core.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleListResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListCreateApprovedArticles {

    private final ArticleQueryRepository articleQueryRepository;

    @Transactional(readOnly = true)
    public List<ArticleListResponse> findApprovedArticles() {
        List<Article> approvedArticles = articleQueryRepository.findApprovedArticles();

        return approvedArticles.stream()
                .map(ArticleListResponse::fromEntity)
                .collect(java.util.stream.Collectors.toList());
    }
}
