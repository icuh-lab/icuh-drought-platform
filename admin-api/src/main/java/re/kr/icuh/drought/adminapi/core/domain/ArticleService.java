package re.kr.icuh.drought.adminapi.core.domain;

import org.springframework.stereotype.Service;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleListResponse;
import re.kr.icuh.drought.adminapi.core.api.controller.v2.response.UpdateArticleResponse;

import java.util.List;

@Service
public class ArticleService {

    private final ArticleFinder articleFinder;

    public ArticleService(ArticleFinder articleFinder) {
        this.articleFinder = articleFinder;
    }

    // status 값에 따라 출력되는 내용이 다르게 리펙토링 해야함
    public List<ArticleListResponse> findArticles(ArticleStatus status) {
        return articleFinder.findArticleByStatus(status);
    }

    public void updateArticleStatus(Long articleId) {
        articleFinder.updateArticleStatus(articleId);
    }

    public void rejectArticle(Long articleId, String reason) {
        articleFinder.rejectArticle(articleId, reason);
    }

    public List<ArticleListResponse> findPendingArticles() {
        return articleFinder.pendingUpdateArticles();
    }

    public UpdateArticleResponse findArticle(Long articleId) { // 변경될 Article의 내용이 노출되어야한다.
        return articleFinder.findArticle(articleId);
    }

    public void mergeArticle(Long articleId) {
        articleFinder.mergeArticle(articleId);
    }
}
