package re.kr.icuh.drought.publicapi.article.infra;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.publicapi.article.domain.Article;
import re.kr.icuh.drought.publicapi.article.domain.QArticle;
import re.kr.icuh.drought.publicapi.article.dto.request.ArticleRequest;

import java.util.List;

@RequiredArgsConstructor
public class ArticleRepositoryImpl implements ArticleRepositoryCustom{

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Page<Article> findApprovedArticles(ArticleRequest request, Pageable pageable) {
        QArticle article = QArticle.article;
        BooleanBuilder builder = new BooleanBuilder();

        if (request.documentType() != null) {
            builder.and(article.documentType.code.eq(request.documentType()));
        }

        if (request.subjectDomain() != null) {
            builder.and(article.subjectDomain.code.eq(request.subjectDomain()));
        }

        if (request.source() != null) {
            builder.and(article.source.eq(request.source()));
        }

        if (request.query() != null) {
            builder.and(article.title.containsIgnoreCase(request.query()));
        }

        // 승인 상태 조건을 묶어서 처리
        builder.andAnyOf(
                article.status.eq(ArticleStatus.APPROVED),
                article.status.eq(ArticleStatus.UPDATED_APPROVED)
        );

        List<Article> articles = jpaQueryFactory
                .selectFrom(article)
                .leftJoin(article.documentType).fetchJoin()
                .leftJoin(article.subjectDomain).fetchJoin()
                .where(builder)
                .orderBy(article.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(article.count())
                .from(article)
                .where(builder);

        return PageableExecutionUtils.getPage(articles, pageable, countQuery::fetchOne);
    }
}
