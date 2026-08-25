package re.kr.icuh.drought.adminapi.core.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleListResponse;

import java.util.List;
import re.kr.icuh.drought.persistence.article.entity.Article;

@Service
@RequiredArgsConstructor
public class ListCreatePendingArticles {

    private final ArticleQueryRepository articleQueryRepository;

    @Transactional(readOnly = true)
    public List<ArticleListResponse> findPendingArticles() {
        List<Article> pendingArticles = articleQueryRepository.findPendingArticles();

        return pendingArticles.stream()
                .map(ArticleListResponse::fromEntity)
                .collect(java.util.stream.Collectors.toList());
    }
}
