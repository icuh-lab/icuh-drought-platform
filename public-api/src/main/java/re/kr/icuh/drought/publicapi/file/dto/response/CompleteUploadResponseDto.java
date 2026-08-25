package re.kr.icuh.drought.publicapi.file.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class CompleteUploadResponseDto {
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private Long fileSize;
    private String extension;
}
