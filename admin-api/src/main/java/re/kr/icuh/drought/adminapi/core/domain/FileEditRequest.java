package re.kr.icuh.drought.adminapi.core.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import re.kr.icuh.drought.domain.article.FileStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_edit_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileEditRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_edit_id", nullable = false)
    private ArticleEditRequest articleEditRequest;

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
    public FileEditRequest(ArticleEditRequest articleEditRequest, String originalFilename, String storedFilename, String filePath, String extension, Long fileSize) {
        this.articleEditRequest = articleEditRequest;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.extension = extension;
        this.status = FileStatus.UPDATED_PENDING;
    }
}
