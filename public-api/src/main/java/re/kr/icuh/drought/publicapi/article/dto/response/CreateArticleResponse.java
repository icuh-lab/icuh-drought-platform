package re.kr.icuh.drought.publicapi.article.dto.response;

public record CreateArticleResponse(
        Long articleId
) {
    public static CreateArticleResponse of(Long articleId) {
        return new CreateArticleResponse(articleId);
    }
}
