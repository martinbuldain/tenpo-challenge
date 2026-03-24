package com.tenpo.challenge.exception;

import com.tenpo.challenge.domain.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 400 Bad Request

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWebExchangeBindException(
            WebExchangeBindException ex, ServerWebExchange exchange) {

        List<String> details = ex.getBindingResult().getAllErrors().stream()
            .map(error -> error instanceof FieldError fe
                ? "%s: %s".formatted(fe.getField(), fe.getDefaultMessage())
                : error.getDefaultMessage())
            .toList();

        String path = exchange.getRequest().getPath().value();
        log.warn("Fallo la validacion [path={}]: {}", path, details);

        return Mono.just(ResponseEntity.badRequest().body(
            ErrorResponse.of(400, "Bad Request",
                "Parametros de entrada invalidos", path, details)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidation(
            MethodArgumentNotValidException ex, ServerWebExchange exchange) {

        List<String> details = ex.getBindingResult().getAllErrors().stream()
            .map(error -> error instanceof FieldError fe
                ? "%s: %s".formatted(fe.getField(), fe.getDefaultMessage())
                : error.getDefaultMessage())
            .toList();

        String path = exchange.getRequest().getPath().value();
        log.warn("Fallo la validacion [path={}]: {}", path, details);

        return Mono.just(ResponseEntity.badRequest().body(
            ErrorResponse.of(400, "Bad Request",
                "Parametros de entrada invalidos", path, details)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConstraintViolation(
            ConstraintViolationException ex, ServerWebExchange exchange) {

        List<String> details = ex.getConstraintViolations().stream()
            .map(cv -> "%s: %s".formatted(cv.getPropertyPath(), cv.getMessage()))
            .toList();

        String path = exchange.getRequest().getPath().value();
        log.warn("Constraint violation [path={}]: {}", path, details);

        return Mono.just(ResponseEntity.badRequest().body(
            ErrorResponse.of(400, "Bad Request",
                "Parametros invalidos", path, details)));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleServerWebInputException(
            ServerWebInputException ex, ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        log.warn("Invalid request input [path={}]: {}", path, ex.getReason());

        return Mono.just(ResponseEntity.badRequest().body(
            ErrorResponse.of(400, "Bad Request",
                "Invalid request: " + ex.getReason(), path)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, ServerWebExchange exchange) {

        String detail = "El parametro '%s' debe ser de tipo %s".formatted(
            ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconocido");

        String path = exchange.getRequest().getPath().value();
        log.warn("Type mismatch [path={}]: {}", path, detail);

        return Mono.just(ResponseEntity.badRequest().body(
            ErrorResponse.of(400, "Bad Request", detail, path)));
    }

    // 429 Too Many Requests

    @ExceptionHandler(RateLimitExceededException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRateLimit(
            RateLimitExceededException ex, ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        log.warn("Rate limit exceeded [path={}, retryAfter={}s]",
            path, ex.getRetryAfterSeconds());

        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(ErrorResponse.of(429, "Too Many Requests", ex.getMessage(), path)));
    }

    // 503 Service Unavailable

    @ExceptionHandler(CacheUnavailableException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleCacheUnavailable(
            CacheUnavailableException ex, ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        log.error("Cache unavailable [path={}]: {}", path, ex.getMessage());

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            ErrorResponse.of(503, "Service Unavailable",
                ex.getMessage(), path)));
    }

    @ExceptionHandler(ExternalServiceException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleExternalService(
            ExternalServiceException ex, ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        log.error("External service error [path={}]: {}", path, ex.getMessage());

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            ErrorResponse.of(503, "Service Unavailable",
                "El servicio externo no esta disponible.",
                path)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleNotFound(
            NoResourceFoundException ex, ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        log.warn("Resource not found [path={}]: {}", path, ex.getMessage());

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse.of(404, "Not Found",
                "Recurso no encontrado.",
                path)));
    }

    // 500 Internal Server Error

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneric(
            Exception ex, ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        log.error("Unhandled exception [path={}]: {}", path, ex.getMessage(), ex);

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse.of(500, "Internal Server Error",
                "Internal error.",
                path)));
    }
}
