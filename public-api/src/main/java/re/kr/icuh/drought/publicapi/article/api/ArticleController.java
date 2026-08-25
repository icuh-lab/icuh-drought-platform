package re.kr.icuh.drought.publicapi.article.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.*;
import re.kr.icuh.drought.publicapi.article.application.ArticleService;
import re.kr.icuh.drought.publicapi.article.dto.request.*;
import re.kr.icuh.drought.publicapi.article.dto.response.ArticleDetailResponse;
import re.kr.icuh.drought.publicapi.article.dto.response.ArticleListResponse;
import re.kr.icuh.drought.publicapi.article.dto.response.CreateArticleResponse;
import re.kr.icuh.drought.common.response.ApiResponse;
import re.kr.icuh.drought.publicapi.global.common.PageResponse;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/articles")
    public ApiResponse<PageResponse<ArticleListResponse>> findArticles(
            ArticleRequest request,
            @PageableDefault(page = 0, size = 10)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.success(articleService.findArticles(request, pageable));
    }

    @GetMapping("/articles/{id}")
    public ApiResponse<ArticleDetailResponse> getArticle(@PathVariable Long id) {
        return ApiResponse.success(articleService.findArticleById(id));
    }

    @PostMapping("/articles/{id}")
    public ApiResponse<ArticleDetailResponse> modifyArticleStatus(@PathVariable Long id, @RequestBody ModifyArticleStatusRequest request) {
        return ApiResponse.success(articleService.modifyArticleStatus(id, request));
    }

    @PatchMapping("/articles/{id}")
    public ApiResponse<Void> updateArticle(@PathVariable Long id, @Valid @RequestBody UpdateArticleRequest request) {
        articleService.updateArticle(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/articles/{articleId}")
    public ApiResponse<Void> deleteArticle(@PathVariable Long articleId, @Valid @RequestBody DeleteArticleRequest request) {
        articleService.deleteArticle(articleId, request);
        return ApiResponse.success();
    }

    // 새로운 통합 API
    @PostMapping("/articles-with-files")
    public ApiResponse<CreateArticleResponse> createArticleWithFiles(@Valid @RequestBody CreateArticleWithFilesRequest request) {
        return ApiResponse.success(articleService.createArticleWithFiles(request));
    }
}
