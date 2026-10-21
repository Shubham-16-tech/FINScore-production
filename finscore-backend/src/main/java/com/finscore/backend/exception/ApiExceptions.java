package com.finscore.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ApiExceptions {
    public static class UnsupportedReportException extends RuntimeException { public UnsupportedReportException(String message) { super(message); } }
    public static class InvalidRequestException extends RuntimeException { public InvalidRequestException(String message) { super(message); } }
}

class UnsupportedReportException extends ApiExceptions.UnsupportedReportException { public UnsupportedReportException(String message) { super(message); } }

@RestControllerAdvice
class GlobalExceptionHandler {
    record ErrorResponse(Instant timestamp, int status, String code, String message, List<String> details, String requestId) {}
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    org.springframework.http.ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException ex) { return response(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The PDF exceeds the 25 MB upload limit.", List.of()); }
    @ExceptionHandler(ApiExceptions.UnsupportedReportException.class)
    org.springframework.http.ResponseEntity<ErrorResponse> unsupported(ApiExceptions.UnsupportedReportException ex) { return response(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_REPORT", ex.getMessage(), List.of("Use the standardized report format described in the sample-reports folder.")); }
    @ExceptionHandler(ApiExceptions.InvalidRequestException.class)
    org.springframework.http.ResponseEntity<ErrorResponse> invalid(ApiExceptions.InvalidRequestException ex) { return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), List.of()); }
    @ExceptionHandler(Exception.class)
    org.springframework.http.ResponseEntity<ErrorResponse> generic(Exception ex) { return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The analysis could not be completed.", List.of()); }
    private org.springframework.http.ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message, List<String> details) { return org.springframework.http.ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(), code, message, details, UUID.randomUUID().toString())); }
}
