package re.kr.icuh.drought.publicapi.article.infra;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import re.kr.icuh.drought.publicapi.article.domain.Article;
import re.kr.icuh.drought.publicapi.article.dto.request.ArticleRequest;

public interface ArticleRepositoryCustom {

    Page<Article> findApprovedArticles(ArticleRequest request, Pageable pageable);
}
