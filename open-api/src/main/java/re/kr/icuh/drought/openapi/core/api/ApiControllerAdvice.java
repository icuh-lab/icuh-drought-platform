package re.kr.icuh.drought.openapi.core.api;

import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import re.kr.icuh.drought.common.openapi.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiControllerAdvice {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @ExceptionHandler(CoreException.class)
    public ResponseEntity<ApiResponse<?>> handleCoreException(CoreException e) {
        switch (e.getErrorType().getLogLevel()) {
            case ERROR -> log.error("CoreException: {}", e.getMessage(), e);
            case WARN -> log.warn("CoreException: {}", e.getMessage(), e);
            default -> log.info("CoreException: {}", e.getMessage(), e);
        }

        return new ResponseEntity<>(ApiResponse.error(e.getErrorType(), e.getData()), e.getErrorType().getStatus());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindException(BindException e) {
        log.warn("BindException: {}", e.getMessage(), e);

        String errorMessage = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return new ResponseEntity<>(
                ApiResponse.error(ErrorType.INVALID_PARAMETER, errorMessage),
                ErrorType.INVALID_PARAMETER.getStatus());
    }

    /**
     * 매핑되지 않은 경로. 이 핸들러가 없으면 아래 catch-all에 걸려 500이 나가고,
     * URL 오타와 실제 장애를 응답만 보고 구분할 수 없게 된다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("NoResourceFoundException: {}", e.getMessage());

        return new ResponseEntity<>(
                ApiResponse.error(ErrorType.ENDPOINT_NOT_FOUND),
                ErrorType.ENDPOINT_NOT_FOUND.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("Exception : {}", e.getMessage(), e);
        return new ResponseEntity<>(ApiResponse.error(ErrorType.DEFAULT_ERROR), ErrorType.DEFAULT_ERROR.getStatus());
    }
}
