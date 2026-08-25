package re.kr.icuh.drought.adminapi.core.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.domain.article.FileStatus;
import re.kr.icuh.drought.persistence.article.entity.Article;
import re.kr.icuh.drought.persistence.article.entity.FileEntity;

@Service
@RequiredArgsConstructor
public class ApproveUpdateArticle {

    private final ArticleQueryRepository articleQueryRepository;

    @Transactional
    public void approveUpdateArticle(Long id) {
        ArticleEditRequest articleEditRequest = articleQueryRepository.findUpdatedRequestArticle(id);
        Article article = articleQueryRepository.findArticle(articleEditRequest.getArticle().getId());


        applyEditRequest(article, articleEditRequest);
        articleEditRequest.changeStatus(ArticleStatus.UPDATED_APPROVED);
    }

    /**
     * 수정 요청(admin 전용 엔티티)의 내용을 게시글에 반영한다.
     * {@code ArticleEditRequest}는 admin 전용 타입이라 공용 {@code Article} 매핑이 참조할 수 없으므로
     * 승인 유스케이스인 이곳이 로직을 갖는다.
     */
    private void applyEditRequest(Article article, ArticleEditRequest articleEditRequest) {
        article.applyApprovedContent(
                articleEditRequest.getTitle(),
                articleEditRequest.getDescription(),
                articleEditRequest.getAuthor(),
                articleEditRequest.getAuthorOrganization(),
                articleEditRequest.getDepartment(),
                articleEditRequest.getSource()
        );
        article.changeStatus(ArticleStatus.UPDATED_APPROVED);
        article.applyApprovedClassification(
                articleEditRequest.getViews(),
                articleEditRequest.getDocumentType(),
                articleEditRequest.getSubjectDomain()
        );

        // 기존 파일 모두 제거
        article.clearFiles();

        // FileEditRequest를 FileEntity로 변환하여 추가
        articleEditRequest.getFiles().forEach(fileEditRequest -> {
            FileEntity fileEntity = FileEntity.builder()
                    .article(article)
                    .originalFilename(fileEditRequest.getOriginalFilename())
                    .storedFilename(fileEditRequest.getStoredFilename())
                    .filePath(fileEditRequest.getFilePath())
                    .extension(fileEditRequest.getExtension())
                    .fileSize(fileEditRequest.getFileSize())
                    .build();

            // 파일 상태 설정 (이전 상태를 유지하거나 APPROVED로 설정)
            fileEntity.changeStatus(FileStatus.APPROVED);

            // 파일 추가
            article.addFile(fileEntity);
        });
    }
}
