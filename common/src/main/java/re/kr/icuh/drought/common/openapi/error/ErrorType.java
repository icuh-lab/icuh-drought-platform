package re.kr.icuh.drought.common.openapi.error;

import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

public enum ErrorType {

    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, ErrorCode.E400, "Invalid parameter format.", Level.WARN),
    DATA_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E404, "Data Not Found", Level.WARN),

    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "An unexpected error has occurred.", Level.ERROR);

    private final HttpStatus status;
    private final ErrorCode code;
    private final String message;
    private final Level logLevel;

    ErrorType(HttpStatus status, ErrorCode code, String message, Level logLevel) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.logLevel = logLevel;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Level getLogLevel() {
        return logLevel;
    }
}
