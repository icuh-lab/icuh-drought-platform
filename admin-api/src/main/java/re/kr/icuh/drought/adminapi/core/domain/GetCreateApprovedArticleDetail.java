package re.kr.icuh.drought.adminapi.core.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticlePendingResponse;

@Service
@RequiredArgsConstructor
public class GetCreateApprovedArticleDetail {

    private final ArticleQueryRepository articleQueryRepository;

    @Transactional(readOnly = true)
    public ArticlePendingResponse findApprovedArticleDetail(Long id) {
        return ArticlePendingResponse.fromEntity(articleQueryRepository.findArticle(id));
    }
}
