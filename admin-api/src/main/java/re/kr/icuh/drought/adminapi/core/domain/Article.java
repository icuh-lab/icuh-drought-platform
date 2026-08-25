package re.kr.icuh.drought.adminapi.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import re.kr.icuh.drought.adminapi.core.api.controller.v2.NewFileRequestJsonConverter;
import re.kr.icuh.drought.adminapi.core.api.controller.v2.UpdateArticleRequestJsonConverter;
import re.kr.icuh.drought.adminapi.core.api.controller.v2.request.UpdateArticleRequest;
import re.kr.icuh.drought.common.crypto.Sha256;
import re.kr.icuh.drought.persistence.article.entity.DocumentType;
import re.kr.icuh.drought.persistence.article.entity.SubjectDomain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // 소프트 삭제 메서드
    public void softDelete() {
        this.status = ArticleStatus.DELETED_PENDING;
        this.updatedAt = LocalDateTime.now();
    }

    // 조회수 증가 메서드
    public void increaseViews() {
        this.views++;
    }

    // 파일 추가 메서드
    public void addFile(FileEntity file) {
        this.files.add(file);
        file.assignArticle(this);
    }

    // 비밀번호 검증 메서드
    public boolean validatePassword(String password) {
        // 실제 구현에서는 암호화된 비밀번호 비교 로직 필요
        return this.tempPassword.equals(sha256Encode(password));
    }

    public void changeStatus(ArticleStatus articleStatus) {
        this.status = articleStatus;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public void updateArticle(ArticleEditRequest articleEditRequest) {
        this.title = articleEditRequest.getTitle();
        this.description = articleEditRequest.getDescription();
        this.author = articleEditRequest.getAuthor();
        this.authorOrganization = articleEditRequest.getAuthorOrganization();
        this.department = articleEditRequest.getDepartment();
        this.updatedAt = LocalDateTime.now();
        this.status = ArticleStatus.UPDATED_APPROVED;
        this.views = articleEditRequest.getViews();
        this.subjectDomain = articleEditRequest.getSubjectDomain();
        this.documentType = articleEditRequest.getDocumentType();
        this.source = articleEditRequest.getSource();

        // 기존 파일 모두 제거
        this.files.clear();

        // FileEditRequest를 FileEntity로 변환하여 추가
        articleEditRequest.getFiles().forEach(fileEditRequest -> {
            FileEntity fileEntity = FileEntity.builder()
                    .article(this)
                    .originalFilename(fileEditRequest.getOriginalFilename())
                    .storedFilename(fileEditRequest.getStoredFilename())
                    .filePath(fileEditRequest.getFilePath())
                    .extension(fileEditRequest.getExtension())
                    .fileSize(fileEditRequest.getFileSize())
                    .build();

            // 파일 상태 설정 (이전 상태를 유지하거나 APPROVED로 설정)
            fileEntity.changeStatus(FileStatus.APPROVED);

            // 파일 추가
            this.addFile(fileEntity);
        });
    }

    public void updateArticleV2(UpdateArticleRequest updateArticleRequest) {
        this.title = updateArticleRequest.title();
        this.description = updateArticleRequest.description();
        this.author = updateArticleRequest.author();
        this.authorOrganization = updateArticleRequest.authorOrganization();
        this.department = updateArticleRequest.department();
        this.updatedAt = LocalDateTime.now();
        this.status = ArticleStatus.APPROVED;
//        this.views = updateArticleRequest.views();
//        this.subjectDomain = updateArticleRequest.subjectDomainId();
//        this.documentType = updateArticleRequest.documentTypeId();
        this.source = updateArticleRequest.source();
        this.files.clear();

        updateArticleRequest.newFiles().forEach(newFileRequest -> {
            FileEntity fileEntity = FileEntity.builder()
                    .article(this)
                    .originalFilename(newFileRequest.originalFileName())
                    .storedFilename(newFileRequest.storedFileName())
                    .filePath(newFileRequest.filePath())
                    .extension(newFileRequest.extension())
                    .fileSize(newFileRequest.fileSize())
                    .build();

            fileEntity.changeStatus(FileStatus.APPROVED);
            this.addFile(fileEntity);
        });
    }

    public void initPendingUpdate() {
        this.pendingUpdate = null;
    }
}
