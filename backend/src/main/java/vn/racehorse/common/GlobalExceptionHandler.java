package vn.racehorse.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.core.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String REQUIRED = "Bắt buộc nhập.";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handle(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), e.fields()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handle(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        String message = "Thông tin chưa hợp lệ: " + String.join(", ", fields.keySet()) + ".";
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", message, fields));
    }

    /** A query or path value of the wrong type or an unknown enum value names the parameter (TECH-175). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handle(MethodArgumentTypeMismatchException e) {
        String message = "Giá trị của \"" + e.getName() + "\" không hợp lệ.";
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", message, Map.of(e.getName(), message)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handle(MissingServletRequestParameterException e) {
        String message = "Thiếu \"" + e.getParameterName() + "\".";
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", message, Map.of(e.getParameterName(), message)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handle(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("MALFORMED_REQUEST", "Dữ liệu gửi lên không đúng định dạng.", Map.of()));
    }

    /** Above the container's multipart limit; only video uploads come close to it (TECH-055). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handle(MaxUploadSizeExceededException e) {
        String message = "Video vượt quá giới hạn 200 MB.";
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("VIDEO_TOO_LARGE", message, Map.of("file", message)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handle(AccessDeniedException e) {
        ApiException denied = ApiException.forbidden();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(denied.code(), denied.getMessage(), Map.of()));
    }

    /** A path no controller serves answers in the app's own error shape, not the framework's default body (TECH-190). */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handle(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "Không tìm thấy trang hoặc dữ liệu được yêu cầu.", Map.of()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handle(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiError("METHOD_NOT_ALLOWED", "Thao tác này không được hỗ trợ cho đường dẫn đã chọn.", Map.of()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handle(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ApiError("UNSUPPORTED_MEDIA_TYPE", "Định dạng dữ liệu gửi lên không được hỗ trợ.", Map.of()));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiError> handle(HttpMediaTypeNotAcceptableException e) {
        // The caller refused JSON, so the body is pinned to it rather than negotiated again.
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(new ApiError("NOT_ACCEPTABLE", "Máy chủ không trả được dữ liệu theo định dạng đã yêu cầu.", Map.of()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> handle(MissingServletRequestPartException e) {
        String message = "Thiếu \"" + e.getRequestPartName() + "\".";
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", message, Map.of(e.getRequestPartName(), message)));
    }

    /** Anything unforeseen: the cause goes to the log, the person gets a plain message and no internals. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e) throws Exception {
        if (e instanceof AuthenticationException) {
            throw e; // the security filter chain answers these
        }
        if (e instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            // A framework refusal not named above keeps its own status.
            return ResponseEntity.status(framework.getStatusCode()).body(new ApiError("BAD_REQUEST",
                    "Yêu cầu không hợp lệ.", Map.of()));
        }
        LOG.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Hệ thống gặp lỗi, vui lòng thử lại sau.", Map.of()));
    }
}
