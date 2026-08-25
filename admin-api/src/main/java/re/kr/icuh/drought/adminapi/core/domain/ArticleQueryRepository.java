package re.kr.icuh.drought.adminapi.core.domain;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ArticleQueryRepository {

    private final JPAQueryFactory queryFactory;

    public ArticleQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public List<Article> findPendingArticles() {
        QArticle article = QArticle.article;
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(article.status.eq(ArticleStatus.PENDING));

        return queryFactory
                .selectFrom(article)
                .leftJoin(article.documentType).fetchJoin()
                .leftJoin(article.subjectDomain).fetchJoin()
                .where(builder)
                .orderBy(article.createdAt.desc())
                .fetch();
    }

    public List<Article> findApprovedArticles() {
        QArticle article = QArticle.article;
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(article.status.eq(ArticleStatus.APPROVED));

        return queryFactory
                .selectFrom(article)
                .leftJoin(article.documentType).fetchJoin()
                .leftJoin(article.subjectDomain).fetchJoin()
                .where(builder)
                .orderBy(article.createdAt.desc())
                .fetch();
    }

    public Article findArticle(Long id) {
        QArticle article = QArticle.article;
        QFileEntity file = QFileEntity.fileEntity;

        BooleanBuilder builder = new BooleanBuilder();

        return queryFactory
                .selectFrom(article)
                .leftJoin(article.documentType).fetchJoin()
                .leftJoin(article.subjectDomain).fetchJoin()
                .leftJoin(article.files, file).fetchJoin()
                .where(builder.and(article.id.eq(id)))
                .fetchOne();
    }


    public List<Article> findDeletePendingArticles() {
        QArticle article = QArticle.article;

        BooleanBuilder builder = new BooleanBuilder();

        builder.and(article.status.eq(ArticleStatus.DELETED_PENDING));

        return queryFactory
                .selectFrom(article)
                .leftJoin(article.documentType).fetchJoin()
                .leftJoin(article.subjectDomain).fetchJoin()
                .where(builder)
                .orderBy(article.createdAt.desc())
                .fetch();
    }

    public List<Article> findDeleteApprovedArticles() {
        QArticle article = QArticle.article;

        BooleanBuilder builder = new BooleanBuilder();

        builder.and(article.status.eq(ArticleStatus.DELETED));

        return queryFactory
                .selectFrom(article)
                .leftJoin(article.documentType).fetchJoin()
                .leftJoin(article.subjectDomain).fetchJoin()
                .where(builder)
                .orderBy(article.createdAt.desc())
                .fetch();
    }

    public List<ArticleEditRequest> findUpdatedPendingArticles() {
        QArticleEditRequest articleEditRequest = QArticleEditRequest.articleEditRequest;
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(articleEditRequest.status.eq(ArticleStatus.UPDATED_PENDING));

        return queryFactory
                .selectFrom(articleEditRequest)
                .leftJoin(articleEditRequest.documentType).fetchJoin()
                .leftJoin(articleEditRequest.subjectDomain).fetchJoin()
                .where(builder)
                .orderBy(articleEditRequest.createdAt.desc())
                .fetch();
    }

    public ArticleEditRequest findUpdatedRequestArticle(Long id) {
        QArticleEditRequest articleEditRequest = QArticleEditRequest.articleEditRequest;
        QArticle article = QArticle.article;

        return queryFactory
                .selectFrom(articleEditRequest)
                .leftJoin(articleEditRequest.article, article).fetchJoin()
                .where(articleEditRequest.id.eq(id))
                .fetchOne();
    }

    public List<Article> findUpdatedApprovedArticles() {
        QArticle article = QArticle.article;
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(article.status.eq(ArticleStatus.UPDATED_APPROVED));

        return queryFactory
                .selectFrom(article)
                .leftJoin(article.documentType).fetchJoin()
                .leftJoin(article.subjectDomain).fetchJoin()
                .where(builder)
                .orderBy(article.createdAt.desc())
                .fetch();
    }

    // 수정 필요
    public ArticleEditRequest findUpdatePendingArticle(Long id) {
        QArticleEditRequest articleEditRequest = QArticleEditRequest.articleEditRequest;
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(articleEditRequest.id.eq(id));
        builder.and(articleEditRequest.status.eq(ArticleStatus.UPDATED_PENDING));

        return queryFactory
                .selectFrom(articleEditRequest)
                .where(builder)
                .fetchOne();
    }
}
