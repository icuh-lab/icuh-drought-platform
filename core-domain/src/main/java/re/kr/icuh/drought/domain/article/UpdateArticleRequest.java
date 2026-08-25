package re.kr.icuh.drought.domain.article;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 게시글 수정 요청. 이 타입 하나가 서로 다른 세 가지 계약을 동시에 진다.
 *
 * <p><b>주의:</b> 아래 세 계약을 모두 지키지 않으면 어느 한쪽이 조용히 깨진다.
 *
 * <ol>
 *   <li><b>HTTP 계약</b> — {@code public-api}의 {@code PATCH /api/v1/articles/{id}}
 *       ({@code ArticleController.updateArticle})가 {@code @Valid @RequestBody}로 그대로 받는다.
 *       필드명은 요청 JSON의 키와 같다.</li>
 *   <li><b>저장 계약</b> — {@code articles.pending_update} 컬럼의 직렬화 형태다.
 *       {@code UpdateArticleRequestJsonConverter}에 {@code @JsonTypeInfo}가 없으므로
 *       필드 <b>이름과 순서</b>가 곧 저장 포맷이다. 필드를 바꾸거나 이름을 바꾸면
 *       이미 저장된 기존 행을 역직렬화할 수 없다.</li>
 *   <li><b>admin 읽기 모델</b> — admin이 스테이징된 수정을 승인할 때
 *       ({@code ArticleFinder}가 {@code pendingUpdate}를 읽어 반영) 이 타입 그대로 읽는다.</li>
 * </ol>
 */
public record UpdateArticleRequest(
        @NotNull String title,
        @NotNull String description,
        @NotNull String author,
        @NotNull String authorOrganization,
        @NotNull String department,
        @NotNull String tempPassword,
        @NotNull String documentTypeCode,
        @NotNull String subjectDomainCode,
        @NotNull String source,
        List<NewFileRequest> newFiles
) {
    public record NewFileRequest(
            String originalFileName,
            String storedFileName,
            String filePath,
            Long fileSize,
            String extension
    ) {}
}

