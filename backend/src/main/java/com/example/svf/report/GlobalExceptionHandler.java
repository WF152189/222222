package com.example.svf.report;

import com.example.svf.svf.SvfCloudException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(SvfCloudException.class)
    public ResponseEntity<Map<String, String>> handleSvfCloudException(SvfCloudException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "code", "SVF_CLOUD_ERROR",
                        "message", ex.getMessage()));
    }
}
