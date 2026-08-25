package re.kr.icuh.drought.adminapi.core.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import re.kr.icuh.drought.domain.article.ArticleStatus;

import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    @Query("SELECT a FROM Article a WHERE a.status = :status ORDER BY a.createdAt DESC")
    List<Article> findArticlesByStatusOrderByCreatedAtDesc(ArticleStatus status);

    @Query("SELECT a FROM Article a WHERE a.status = 'APPROVED' AND a.pendingUpdate IS NOT NULL ORDER BY a.createdAt DESC")
    List<Article> findPendingUpdateArticle();
}
