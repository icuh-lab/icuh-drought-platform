package re.kr.icuh.drought.adminapi.core.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.domain.article.ArticleStatus;

@Service
@RequiredArgsConstructor
public class ApproveUpdateArticle {

    private final ArticleQueryRepository articleQueryRepository;

    @Transactional
    public void approveUpdateArticle(Long id) {
        ArticleEditRequest articleEditRequest = articleQueryRepository.findUpdatedRequestArticle(id);
        Article article = articleQueryRepository.findArticle(articleEditRequest.getArticle().getId());


        article.updateArticle(articleEditRequest);
        articleEditRequest.changeStatus(ArticleStatus.UPDATED_APPROVED);
    }
}
