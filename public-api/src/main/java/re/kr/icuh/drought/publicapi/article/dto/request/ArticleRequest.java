package re.kr.icuh.drought.publicapi.article.dto.request;

public record ArticleRequest(
        String documentType,
        String subjectDomain,
        String source,
        String query
) {}
