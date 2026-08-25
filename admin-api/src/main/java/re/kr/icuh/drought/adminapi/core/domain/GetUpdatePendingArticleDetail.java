package re.kr.icuh.drought.adminapi.core.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleUpdatePendingResponse;

@Service
@RequiredArgsConstructor
public class GetUpdatePendingArticleDetail {

    private final ArticleQueryRepository articleQueryRepository;

    @Transactional(readOnly = true)
    public ArticleUpdatePendingResponse findUpdatePendingArticle(Long id) {
        return ArticleUpdatePendingResponse.fromEntity(articleQueryRepository.findUpdatePendingArticle(id));
    }

    // 입력은 articleeditRequestID
//    @Transactional(readOnly = true)
//    public ArticleUpdatePendingResponse findUpdatePendingArticle(Long id) {
//
//        // 수정이 완료된 게시글 조회
//        Article article = articleQueryRepository.findArticle(id);
//        ArticleEditRequest updatePendingArticle = articleQueryRepository.findUpdatePendingArticle(id);
//        if (article.getId() != null && (article.getStatus() == ArticleStatus.UPDATED_APPROVED)) {
//            return ArticleUpdatePendingResponse.fromEntity(article);
//        }
//
//        return ArticleUpdatePendingResponse.fromEntity(articleQueryRepository.findUpdatePendingArticle(id));
//    }
}
