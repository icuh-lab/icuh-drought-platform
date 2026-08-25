package re.kr.icuh.drought.publicapi.file.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import re.kr.icuh.drought.domain.article.FileStatus;
import re.kr.icuh.drought.publicapi.article.domain.Article;

import java.time.LocalDateTime;

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
        this.status = status;
    }

    // 소프트 삭제 메서드
    // TODO: 파일 자체에서 status를 관리할 필요가 있나..?
    public void softDelete() {
        this.status = FileStatus.DELETED_PENDING;
    }


}
