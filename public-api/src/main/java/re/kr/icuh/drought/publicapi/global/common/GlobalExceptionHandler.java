package re.kr.icuh.drought.publicapi.global.common;

import lombok.extern.slf4j.Slf4j;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;
import re.kr.icuh.drought.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.error("Business exception occurred: {}", e.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getStatus(), errorCode.getMessage(), errorCode.getCode(), e.getMessage()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindException(BindException e) {
        log.error("Validation exception occurred: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(
                        ErrorCode.INVALID_INPUT.getStatus(),
                        ErrorCode.INVALID_INPUT.getMessage(),
                        ErrorCode.INVALID_INPUT.getCode(),
                        e.getBindingResult().getAllErrors().get(0).getDefaultMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("File size exceeded: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.FILE_SIZE_EXCEEDED.getStatus())
                .body(ApiResponse.error(
                        ErrorCode.FILE_SIZE_EXCEEDED.getStatus(),
                        ErrorCode.FILE_SIZE_EXCEEDED.getMessage(),
                        ErrorCode.FILE_SIZE_EXCEEDED.getCode(),
                        "업로드 파일 크기가 제한을 초과했습니다."));
    }

    /**
     * 매핑되지 않은 경로. 이 핸들러가 없으면 아래 catch-all에 걸려 500이 나가고,
     * URL 오타와 실제 장애를 응답만 보고 구분할 수 없게 된다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("No handler for path: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.ENDPOINT_NOT_FOUND.getStatus())
                .body(ApiResponse.error(
                        ErrorCode.ENDPOINT_NOT_FOUND.getStatus(),
                        ErrorCode.ENDPOINT_NOT_FOUND.getMessage(),
                        ErrorCode.ENDPOINT_NOT_FOUND.getCode(),
                        "요청하신 경로를 찾을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("Unexpected exception occurred", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_SERVER_ERROR.getStatus(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        "서버 내부 오류가 발생했습니다."));
    }

}
