package re.kr.icuh.drought.publicapi.global.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class FileUtils {

    /**
     * 파일 저장을 위한 이름을 생성합니다.
     * UUID를 사용하여 고유한 파일명을 생성합니다.
     */
    public String createStoreFileName(String originName) {
        String uuid = UUID.randomUUID().toString();
        return uuid + "." + extractExtensionName(originName);
    }

    /**
     * 파일명에서 확장자를 추출합니다.
     */
    public String extractExtensionName(String originName) {
        int position = originName.lastIndexOf(".");
        return position > -1 ? originName.substring(position + 1) : "";
    }
}
