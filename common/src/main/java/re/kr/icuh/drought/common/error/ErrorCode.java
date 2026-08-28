package re.kr.icuh.drought.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "유효하지 않은 입력값입니다."),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "MISSING_REQUIRED_FIELD", "필수 필드가 누락되었습니다."),
    DOCUMENT_TYPE_NOT_FOUND(HttpStatus.BAD_REQUEST, "DOCUMENT_TYPE_NOT_FOUND", "문서 성격을 찾을 수 없습니다. 관리자에게 문의해주세요."),
    SUBJECT_DOMAIN_NOT_FOUND(HttpStatus.BAD_REQUEST, "SUBJECT_DOMAIN_NOT_FOUND", "주제 영역을 찾을 수 없습니다. 관리자에게 문의해주세요."),

    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "비밀번호가 일치하지 않습니다."),

    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTICLE_NOT_FOUND", "해당 게시글을 찾을 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "해당 파일을 찾을 수 없습니다."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", "요청하신 경로를 찾을 수 없습니다."),

    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_SIZE_EXCEEDED", "파일 크기 제한을 초과했습니다."),

    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "지원하지 않는 파일 형식입니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    FILE_DOWNLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_DOWNLOAD_ERROR", "파일 다운로드 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
