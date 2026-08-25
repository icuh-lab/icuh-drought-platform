package re.kr.icuh.drought.publicapi.article.dto.request;

public record DeleteArticleRequest(
        String reason,
        String password
) {
}
