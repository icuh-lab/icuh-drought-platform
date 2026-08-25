package re.kr.icuh.drought.publicapi.article.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.testcontainers.shaded.com.google.common.hash.Hashing;
import re.kr.icuh.drought.publicapi.article.dto.UpdateArticleRequestJsonConverter;
import re.kr.icuh.drought.publicapi.article.dto.request.UpdateArticleRequest;
import re.kr.icuh.drought.persistence.article.entity.DocumentType;
import re.kr.icuh.drought.persistence.article.entity.SubjectDomain;
import re.kr.icuh.drought.publicapi.file.domain.FileEntity;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
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

    @Column(name = "pending_update")
    @Convert(converter = UpdateArticleRequestJsonConverter.class)
    private UpdateArticleRequest pendingUpdate;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FileEntity> files = new ArrayList<>();

    @Builder
    public Article(String title, String description, String author, String authorOrganization, String department, String tempPassword, Integer views, ArticleStatus status, DocumentType documentType, SubjectDomain subjectDomain, String source, Boolean isDeleted, LocalDateTime deletedAt) {
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
        this.pendingUpdate = null;
    }

    public String sha256Encode(String tempPassword) {
        return Hashing.sha256().hashString(tempPassword, StandardCharsets.UTF_8).toString();
    }

    public void delete() {
        this.status = ArticleStatus.DELETED_PENDING;
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void reject() {
        if (this.status != ArticleStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        this.status = ArticleStatus.REJECTED;
    }

    public void increaseViews() {
        this.views++;
    }

    public boolean validatePassword(String password) {
        return this.tempPassword.equals(sha256Encode(password));
    }

    public void updateContent(UpdateArticleRequest request) {
        this.pendingUpdate = request;
    }
}
