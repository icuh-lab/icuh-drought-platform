package re.kr.icuh.drought.adminapi.core.domain;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleListResponse;
import re.kr.icuh.drought.adminapi.core.api.controller.v2.response.UpdateArticleResponse;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.domain.article.FileStatus;
import re.kr.icuh.drought.domain.article.UpdateArticleRequest;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;
import re.kr.icuh.drought.persistence.article.entity.Article;
import re.kr.icuh.drought.persistence.article.entity.DocumentType;
import re.kr.icuh.drought.persistence.article.entity.FileEntity;
import re.kr.icuh.drought.persistence.article.entity.SubjectDomain;
import re.kr.icuh.drought.persistence.article.repository.DocumentTypeRepository;
import re.kr.icuh.drought.persistence.article.repository.SubjectDomainRepository;

import java.util.List;

@Component
public class ArticleFinder {

    private final ArticleRepository articleRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final SubjectDomainRepository subjectDomainRepository;

    public ArticleFinder(ArticleRepository articleRepository, DocumentTypeRepository documentTypeRepository, SubjectDomainRepository subjectDomainRepository) {
        this.articleRepository = articleRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.subjectDomainRepository = subjectDomainRepository;
    }

    @Transactional(readOnly = true)
    public List<ArticleListResponse> findArticleByStatus(ArticleStatus status) {
        return articleRepository.findArticlesByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(ArticleListResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void updateArticleStatus(Long articleId) {
        Article savedArticle = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found"));

        savedArticle.changeStatus(ArticleStatus.APPROVED);
    }

    @Transactional
    public void rejectArticle(Long articleId, String reason) {
        Article savedArticle = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found"));

        savedArticle.changeStatus(ArticleStatus.REJECTED);
        savedArticle.assignRejectReason(reason);
    }

    @Transactional(readOnly = true)
    public List<ArticleListResponse> pendingUpdateArticles() {
        return articleRepository.findPendingUpdateArticle()
                .stream()
                .map(ArticleListResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true) // 여기서 새로 변경될 데이터가 노출이 되도록해야함
    public UpdateArticleResponse findArticle(Long articleId) {
        Article savedArticle = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found"));

        // pending_update에 있는 내용을 넘겨줘야한다.
        if (savedArticle.getPendingUpdate() == null) {
            throw new IllegalArgumentException("Article not pending update");
        }

        UpdateArticleRequest pendingUpdate = savedArticle.getPendingUpdate();

        DocumentType documentType = documentTypeRepository.findByCode(pendingUpdate.documentTypeCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND));

        SubjectDomain subjectDomain = subjectDomainRepository.findByCode(pendingUpdate.subjectDomainCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBJECT_DOMAIN_NOT_FOUND));

        return UpdateArticleResponse.of(savedArticle.getId(), savedArticle.getStatus(), pendingUpdate, savedArticle.getUpdatedAt(), documentType, subjectDomain);
    }

    @Transactional
    public void mergeArticle(Long articleId) {
        Article savedArticle = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found"));

        if (savedArticle.getPendingUpdate() == null) {
            throw new IllegalArgumentException("Article not pending update");
        }

        // PendingUpdate 컬럼의 내용을 다시 역직렬화하여 객체로 전환 후 업데이트
        applyPendingUpdate(savedArticle, savedArticle.getPendingUpdate());
        savedArticle.initPendingUpdate();
    }

    /**
     * 승인 대기 중이던 수정 내용을 게시글에 반영하고 승인 상태로 전이한다.
     * 승인 시 상태를 바꾸는 것은 admin의 워크플로우 관심사이므로 공용 {@code Article} 매핑이 아니라
     * 이 유스케이스가 로직을 갖는다.
     */
    private void applyPendingUpdate(Article article, UpdateArticleRequest updateArticleRequest) {
        article.applyApprovedContent(
                updateArticleRequest.title(),
                updateArticleRequest.description(),
                updateArticleRequest.author(),
                updateArticleRequest.authorOrganization(),
                updateArticleRequest.department(),
                updateArticleRequest.source()
        );
        article.changeStatus(ArticleStatus.APPROVED);
//        this.views = updateArticleRequest.views();
//        this.subjectDomain = updateArticleRequest.subjectDomainId();
//        this.documentType = updateArticleRequest.documentTypeId();
        article.clearFiles();

        updateArticleRequest.newFiles().forEach(newFileRequest -> {
            FileEntity fileEntity = FileEntity.builder()
                    .article(article)
                    .originalFilename(newFileRequest.originalFileName())
                    .storedFilename(newFileRequest.storedFileName())
                    .filePath(newFileRequest.filePath())
                    .extension(newFileRequest.extension())
                    .fileSize(newFileRequest.fileSize())
                    .build();

            fileEntity.changeStatus(FileStatus.APPROVED);
            article.addFile(fileEntity);
        });
    }
}
