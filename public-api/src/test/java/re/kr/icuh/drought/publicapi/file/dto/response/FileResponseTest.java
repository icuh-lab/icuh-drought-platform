package re.kr.icuh.drought.publicapi.file.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import re.kr.icuh.drought.domain.article.FileStatus;
import re.kr.icuh.drought.publicapi.file.domain.FileEntity;

import static org.assertj.core.api.Assertions.assertThat;

class FileResponseTest {

    @Test
    @DisplayName("downloadUrl은 실제 다운로드 엔드포인트 경로(/api/v1/multipart-upload/files/{id}/download)와 일치한다")
    void downloadUrl은_실제_다운로드_엔드포인트_경로와_일치한다() {
        // given
        FileEntity file = FileEntity.builder()
                .originalFilename("report.pdf")
                .storedFilename("uuid.pdf")
                .filePath("upload/uuid.pdf")
                .fileSize(1024L)
                .extension("pdf")
                .status(FileStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(file, "id", 42L);

        // when
        FileResponse response = FileResponse.fromEntity(file);

        // then
        assertThat(response.downloadUrl()).isEqualTo("/api/v1/multipart-upload/files/42/download");
    }
}
