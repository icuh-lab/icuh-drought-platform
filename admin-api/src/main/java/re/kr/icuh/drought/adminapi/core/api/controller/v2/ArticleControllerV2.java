package re.kr.icuh.drought.adminapi.core.api.controller.v2;

import org.springframework.web.bind.annotation.*;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleListResponse;
import re.kr.icuh.drought.adminapi.core.api.controller.v2.response.UpdateArticleResponse;
import re.kr.icuh.drought.adminapi.core.domain.ArticleService;
import re.kr.icuh.drought.adminapi.core.domain.ArticleStatus;

import java.util.List;

@RestController
public class ArticleControllerV2 {
    // NOTES 컨트롤러의 역할을 요청을 받고, 응답 객체를 서빙하는 역할을 수행함

    private final ArticleService articleService;

    public ArticleControllerV2(ArticleService articleService) {
        this.articleService = articleService;
    }

    // 여기서 분기처리를 할 필요가 있나?..서빙하는 레이어인데?, 코어한 비지니스 로직은 아니지만 그렇다고 DB 예외 처리는 아니니 서비스 레이어에 작성해보자
    @GetMapping("/api/v2/articles")
    public List<ArticleListResponse> findArticles(@RequestParam(required = false) ArticleStatus status) {
        return articleService.findArticles(status);
    }

    @GetMapping("/api/v2/pending-articles")
    public List<ArticleListResponse> findPendingUpdatedArticle() {
        return articleService.findPendingArticles();
    }

    @PatchMapping("/api/v2/articles/{articleId}")
    public void updateArticleStatus(@PathVariable Long articleId) {
        articleService.updateArticleStatus(articleId);
    }

    @PostMapping("/api/v2/articles/{articleId}/reject") // 해당 게시글의 상태값을 변경하는건데 post가 맞을까?
    public void rejectArticle(@PathVariable Long articleId, @RequestBody String reason) {
        articleService.rejectArticle(articleId, reason);
    }

    @GetMapping("/api/v2/articles/{articleId}/pending-update")
    public UpdateArticleResponse findArticle(@PathVariable Long articleId) {
        return articleService.findArticle(articleId);
    }

    /**
     * 수정 요청 승인
     */
    @PatchMapping("/api/v2/articles/{articleId}/merge")
    public void mergeArticle(@PathVariable Long articleId) {
        articleService.mergeArticle(articleId);
    }
}
