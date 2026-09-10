package io.tradeops.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(OperationException.class)
  ResponseEntity<ApiError> operation(OperationException exception, HttpServletRequest request) {
    return ResponseEntity.status(exception.status())
        .body(ApiError.of(exception.code(), "요청을 처리할 수 없습니다.", request));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiError> persistenceUnavailable(
      DataAccessException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiError.of(
                "PERSISTENCE_UNAVAILABLE",
                "Data could not be saved or loaded. Try again later.",
                request));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpectedError(Exception exception, HttpServletRequest request) {
    if (exception instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
      return ResponseEntity.status(error.getStatusCode())
          .body(
              ApiError.of(
                  error.getStatusCode().value() == 404 ? "NOT_FOUND" : "INVALID_REQUEST",
                  "요청 주소나 입력 내용을 확인해 주세요.",
                  request));
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiError.of("INTERNAL_ERROR", "The request could not be completed.", request));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiError> invalidRequest(Exception exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(ApiError.of("INVALID_REQUEST", "Request validation failed.", request));
  }
}
