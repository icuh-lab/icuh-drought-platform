package re.kr.icuh.drought.publicapi.article.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.domain.article.FileStatus;
import re.kr.icuh.drought.domain.article.UpdateArticleRequest;
import re.kr.icuh.drought.persistence.article.entity.Article;
import re.kr.icuh.drought.publicapi.article.dto.request.*;
import re.kr.icuh.drought.publicapi.article.dto.response.ArticleDetailResponse;
import re.kr.icuh.drought.publicapi.article.dto.response.ArticleListResponse;
import re.kr.icuh.drought.publicapi.article.dto.response.CreateArticleResponse;
import re.kr.icuh.drought.publicapi.article.infra.ArticleRepository;
import re.kr.icuh.drought.persistence.article.entity.DocumentType;
import re.kr.icuh.drought.persistence.article.entity.SubjectDomain;
import re.kr.icuh.drought.persistence.article.repository.DocumentTypeRepository;
import re.kr.icuh.drought.persistence.article.repository.SubjectDomainRepository;
import re.kr.icuh.drought.persistence.article.entity.FileEntity;
import re.kr.icuh.drought.persistence.article.repository.FileRepository;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;
import re.kr.icuh.drought.publicapi.global.common.PageResponse;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final FileRepository fileRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final SubjectDomainRepository subjectDomainRepository;

    @Transactional(readOnly = true)
    public PageResponse<ArticleListResponse> findArticles(ArticleRequest request, Pageable pageable) {
        Page<ArticleListResponse> articleListResponsePage = articleRepository.findApprovedArticles(request, pageable)
                .map(ArticleListResponse::fromEntity);

        return PageResponse.from(articleListResponsePage);
    }

    @Transactional
    public ArticleDetailResponse findArticleById(Long id) {
        Article savedArticle = findSavedArticle(id);
        savedArticle.increaseViews();

        return ArticleDetailResponse.of(savedArticle);
    }

    @Transactional
    public ArticleDetailResponse modifyArticleStatus(Long id, ModifyArticleStatusRequest request) {
        Article article = findSavedArticle(id);
        validatePassword(article, request.password());
        article.reject();
        log.info("게시글 거절 처리: id={}, reason={}", id, request.reason());

        return ArticleDetailResponse.of(article);
    }

    @Transactional
    public void updateArticle(Long id, UpdateArticleRequest request) {
        Article savedArticle = findSavedArticle(id);
        savedArticle.updateContent(request);
    }

    @Transactional
    public void deleteArticle(Long articleId, DeleteArticleRequest request) {
        Article savedArticle = findSavedArticle(articleId);
        validatePassword(savedArticle, request.password());

        savedArticle.delete();
    }

    @Transactional
    public CreateArticleResponse createArticleWithFiles(CreateArticleWithFilesRequest request) {
        // 1. 게시글 저장
        DocumentType documentType = validateDocumentType(request.documentTypeCode());
        SubjectDomain subjectDomain = validateSubjectDomain(request.subjectDomainCode());

        Article article = Article.builder()
                .title(request.title())
                .description(request.description())
                .author(request.author())
                .authorOrganization(request.authorOrganization())
                .department(request.department())
                .tempPassword(request.tempPassword())
                .views(0)
                .status(ArticleStatus.PENDING)
                .documentType(documentType)
                .subjectDomain(subjectDomain)
                .source(request.source())
                .isDeleted(false)
                .deletedAt(null)
                .build();

        articleRepository.save(article);


        // 2. 파일 메타데이터 저장 (게시글 ID와 연결)
        List<FileEntity> files = request.completedFiles().stream()
                .map(fileInfo -> FileEntity.builder()
                        .article(article)
                        .originalFilename(fileInfo.originalFileName())
                        .storedFilename(fileInfo.storedFileName())
                        .filePath(fileInfo.filePath())
                        .fileSize(fileInfo.fileSize())
                        .extension(fileInfo.extension())
                        .status(FileStatus.PENDING)
                        .build()
                )
                .collect(Collectors.toList());

        fileRepository.saveAll(files);

        return CreateArticleResponse.of(article.getId());
    }

    private static void validatePassword(Article article, String password) {
        if (!article.validatePassword(password)) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
    }

    private Article findSavedArticle(Long articleId) {
        return articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
    }

    private DocumentType validateDocumentType(String code) {
        return documentTypeRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND));
    }

    private SubjectDomain validateSubjectDomain(String code) {
        return subjectDomainRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBJECT_DOMAIN_NOT_FOUND));
    }
}
