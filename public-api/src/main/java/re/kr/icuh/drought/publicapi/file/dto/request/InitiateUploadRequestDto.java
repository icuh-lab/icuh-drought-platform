package re.kr.icuh.drought.publicapi.file.dto.request;

import lombok.Data;

@Data
public class InitiateUploadRequestDto {
    private String fileName;
    private String fileType;
    private Long fileSize;
}
