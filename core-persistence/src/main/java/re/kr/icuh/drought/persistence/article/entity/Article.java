package re.kr.icuh.drought.persistence.article.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import re.kr.icuh.drought.common.crypto.Sha256;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.domain.article.UpdateArticleRequest;
import re.kr.icuh.drought.persistence.article.converter.NewFileRequestJsonConverter;
import re.kr.icuh.drought.persistence.article.converter.UpdateArticleRequestJsonConverter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code articles} 테이블에 대한 단일 JPA 매핑.
 * public-api(등록·조회·수정요청·삭제요청)와 admin-api(승인 워크플로우)가 함께 쓴다.
 */
@Entity
@Table(name = "articles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "author")
    private String author;

    @Column(name = "author_organization")
    private String authorOrganization;

    @Column(name = "department")
    private String department;

    @Column(name = "temp_password")
    private String tempPassword;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "views")
    private Integer views;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ArticleStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id")
    private DocumentType documentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_domain_id")
    private SubjectDomain subjectDomain;

    @Column(name = "source")
    private String source;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "pending_update")
    @Convert(converter = UpdateArticleRequestJsonConverter.class)
    private UpdateArticleRequest pendingUpdate;

    @Column(name = "pending_file_update")
    @Convert(converter = NewFileRequestJsonConverter.class)
    private List<UpdateArticleRequest.NewFileRequest> pendingFileUpdate;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FileEntity> files = new ArrayList<>();

    @Builder
    public Article(String title, String description, String author, String authorOrganization, String department, String tempPassword, Integer views, ArticleStatus status, DocumentType documentType, SubjectDomain subjectDomain, String source, Boolean isDeleted, LocalDateTime deletedAt, String rejectReason) {
        this.title = title;
        this.description = description;
        this.author = author;
        this.authorOrganization = authorOrganization;
        this.department = department;
        this.tempPassword = sha256Encode(tempPassword);
        this.views = views == null ? 0 : views;
        this.status = status == null ? ArticleStatus.PENDING : status;
        this.documentType = documentType;
        this.subjectDomain = subjectDomain;
        this.source = source;
        this.isDeleted = isDeleted;
        this.deletedAt = deletedAt;
        this.rejectReason = rejectReason;
    }

    public String sha256Encode(String tempPassword) {
        return Sha256.hexOf(tempPassword);
    }

    public boolean validatePassword(String password) {
        return this.tempPassword.equals(sha256Encode(password));
    }

    public void increaseViews() {
        this.views++;
    }

    /** 삭제 요청. 관리자 승인 전까지는 삭제 대기 상태로만 표시한다. */
    public void delete() {
        this.status = ArticleStatus.DELETED_PENDING;
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    /** 등록 대기 중인 게시글을 거절한다. */
    public void reject() {
        if (this.status != ArticleStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        this.status = ArticleStatus.REJECTED;
    }

    /**
     * 상태를 전이한다. 허용 여부 판단은 {@link ArticleStatus#canTransitionTo(ArticleStatus)} 한 곳에만 있다.
     *
     * <p>현재 상태가 {@code null}인 레코드(상태 컬럼이 NULL인 과거 데이터)는 소스가 없으므로 검사하지 않는다.
     * 지금 성공하는 요청이 이 가드 때문에 새로 실패하면 그것 자체가 HTTP 계약 변경이기 때문이다.
     */
    public void changeStatus(ArticleStatus articleStatus) {
        if (this.status != null && !this.status.canTransitionTo(articleStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        this.status = articleStatus;
    }

    public void assignRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    /** 수정 요청 내용을 승인 전까지 {@code pending_update}에 보관한다. */
    public void updateContent(UpdateArticleRequest request) {
        this.pendingUpdate = request;
    }

    public void initPendingUpdate() {
        this.pendingUpdate = null;
    }

    public void addFile(FileEntity file) {
        this.files.add(file);
        file.assignArticle(this);
    }

    public void clearFiles() {
        this.files.clear();
    }

    /**
     * 승인된 수정 내용을 본문 필드에 반영한다.
     * 어떤 상태로 전이할지(그리고 무엇을 승인으로 볼지)는 admin-api의 승인 유스케이스가 정한다.
     */
    public void applyApprovedContent(String title, String description, String author, String authorOrganization, String department, String source) {
        this.title = title;
        this.description = description;
        this.author = author;
        this.authorOrganization = authorOrganization;
        this.department = department;
        this.source = source;
        this.updatedAt = LocalDateTime.now();
    }

    /** 승인된 수정 내용 중 분류·조회수를 반영한다. */
    public void applyApprovedClassification(Integer views, DocumentType documentType, SubjectDomain subjectDomain) {
        this.views = views;
        this.documentType = documentType;
        this.subjectDomain = subjectDomain;
    }
}
