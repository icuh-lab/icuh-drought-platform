package re.kr.icuh.drought.publicapi.article.dto.request;

public record ModifyArticleStatusRequest(
    String password,
    String reason
) {}
