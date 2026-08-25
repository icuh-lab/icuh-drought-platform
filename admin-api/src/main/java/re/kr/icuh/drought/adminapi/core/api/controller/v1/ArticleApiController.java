package re.kr.icuh.drought.adminapi.core.api.controller.v1;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleEditRequestListResponse;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleListResponse;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticlePendingResponse;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleUpdatePendingResponse;
import re.kr.icuh.drought.adminapi.core.domain.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class ArticleApiController {

    private final ListCreatePendingArticles listCreatePendingArticles;
    private final ListCreateApprovedArticles listCreateApprovedArticles;
    private final GetCreatePendingArticleDetail getCreatePendingArticleDetail;
    private final ApproveCreateArticle approveCreateArticle;

    private final ListDeletePendingArticles listDeletePendingArticles;
    private final ListDeleteApprovedArticles listDeleteApprovedArticles;
    private final GetUpdatePendingArticleDetail getUpdatePendingArticleDetail;
    private final ApproveDeleteArticle approveDeleteArticle;

    private final ListUpdatePendingArticles listUpdatePendingArticles;
    private final ListUpdateApprovedArticles listUpdateApprovedArticles;
    private final ApproveUpdateArticle approveUpdateArticle;

    // 생성 요청: 대기 목록
    @GetMapping("/articles/pending")
    public List<ArticleListResponse> findPendingArticles() {
        return listCreatePendingArticles.findPendingArticles();
    }

    // 생성 요청: 승인 목록
    @GetMapping("/articles/approved")
    public List<ArticleListResponse> findApprovedArticles() {
        return listCreateApprovedArticles.findApprovedArticles();
    }

    // 생성 요청: 상세 (대기)
    @GetMapping("/articles/{id}")
    public ArticlePendingResponse findArticle(@PathVariable Long id) {
        return getCreatePendingArticleDetail.findPendingArticleDetail(id);
    }

    // 생성 요청: 승인
    @PostMapping("/articles/{id}")
    public void approveArticle(@PathVariable Long id) {
        approveCreateArticle.approveCreateArticle(id);
    }

    // 삭제 요청: 대기 목록
    @GetMapping("/articles/delete-list")
    public List<ArticleListResponse> findDeletePendingArticles() {
        return listDeletePendingArticles.findDeletePendingArticles();
    }

    // 삭제 요청: 승인 목록
    @GetMapping("/articles/delete-approved")
    public List<ArticleListResponse> findDeleteApprovedArticles() {
        return listDeleteApprovedArticles.findDeleteApprovedArticles();
    }

    // 삭제 요청: 승인
    @PatchMapping("/articles/{id}")
    public void deleteApproveArticle(@PathVariable Long id) {
        approveDeleteArticle.approveDeleteArticle(id);
    }

    // 수정 요청: 대기 목록
    @GetMapping("/articles/updated-pending")
    public List<ArticleEditRequestListResponse> findUpdatePendingArticles() {
        return listUpdatePendingArticles.findUpdatePendingArticles();
    }

    // 수정 요청: 승인 목록
    @GetMapping("/articles/updated-approved")
    public List<ArticleListResponse> findUpdateApprovedArticles() {
        return listUpdateApprovedArticles.findUpdateApprovedArticles();
    }

    // 수정 요청: 상세보기
    @GetMapping("/articles/update-detail/{id}")
    public ArticleUpdatePendingResponse findUpdateArticleDetail(@PathVariable Long id) {
        return getUpdatePendingArticleDetail.findUpdatePendingArticle(id);
    }

    // 수정 요청: 승인(덮어쓰기 반영)
    @PatchMapping("/articles/update/{id}")
    public void updateApproveArticle(@PathVariable Long id) {
        approveUpdateArticle.approveUpdateArticle(id);
    }
}
