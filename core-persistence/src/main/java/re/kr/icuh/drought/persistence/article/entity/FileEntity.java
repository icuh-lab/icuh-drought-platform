package re.kr.icuh.drought.persistence.article.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import re.kr.icuh.drought.domain.article.FileStatus;

import java.time.LocalDateTime;

/**
 * {@code files} 테이블에 대한 단일 JPA 매핑.
 * public-api(업로드·다운로드)와 admin-api(승인 워크플로우)가 함께 쓴다.
 */
@Entity
@Table(name = "files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "stored_filename")
    private String storedFilename;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "extension")
    private String extension;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileStatus status;

    @Builder
    public FileEntity(Article article, String originalFilename, String storedFilename, String filePath, Long fileSize, String extension, FileStatus status) {
        this.article = article;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.extension = extension;
        this.status = status == null ? FileStatus.PENDING : status;
    }

    public void changeStatus(FileStatus fileStatus) {
        this.status = fileStatus;
    }

    public void assignArticle(Article article) {
        this.article = article;
    }
}
