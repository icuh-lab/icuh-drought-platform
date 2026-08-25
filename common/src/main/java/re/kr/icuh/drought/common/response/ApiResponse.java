package re.kr.icuh.drought.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int status;
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ErrorResponse error;

    public static <T> ApiResponse<T> success(HttpStatus status, String message, T data) {
        return ApiResponse.<T>builder()
                .status(status.value())
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(HttpStatus.OK, "Success", data);
    }

    public static <T> ApiResponse<T> success() {
        return success(HttpStatus.OK, "Success", null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return success(HttpStatus.CREATED, "Created successfully", data);
    }

    public static ApiResponse<?> error(HttpStatus status, String message, String errorCode, String details) {
        return ApiResponse.builder()
                .status(status.value())
                .message(message)
                .error(new ErrorResponse(errorCode, details))
                .build();
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ErrorResponse {
        private String code;
        private String details;
    }
}
