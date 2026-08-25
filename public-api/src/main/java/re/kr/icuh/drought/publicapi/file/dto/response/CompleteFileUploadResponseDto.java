package re.kr.icuh.drought.publicapi.file.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class CompleteFileUploadResponseDto {
    private String uploadId;
    private String fileName;
    private String location;
    private String ETag;
}
