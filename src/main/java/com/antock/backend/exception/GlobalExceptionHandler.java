package com.antock.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // favicon.ico 관련 예외 처리
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        // favicon.ico 요청인 경우 로그를 남기지 않고 404 반환
        if (e.getMessage() != null && e.getMessage().contains("favicon.ico")) {
            return ResponseEntity.notFound().build();
        }

        // 다른 리소스 관련 예외는 일반 예외처럼 처리
        log.error("Resource not found: {}", e.getMessage());
        return ResponseEntity.notFound().build();
    }

    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unhandled exception occurred", e);
        
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", "An unexpected error occurred");
        errorResponse.put("error", e.getMessage());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}