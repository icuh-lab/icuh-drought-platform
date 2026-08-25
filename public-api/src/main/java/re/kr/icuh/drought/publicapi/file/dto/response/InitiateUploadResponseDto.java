package re.kr.icuh.drought.publicapi.file.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InitiateUploadResponseDto {
    private String uploadId;
    private String fileName;
}
