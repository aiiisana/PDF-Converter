package com.example.pdfconverter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class GlobalExceptionHandler {
    //exception for too large files
    @ExceptionHandler(FileTooLargeRedirectException.class)
    public ResponseEntity<Map<String, String>> handleTooLarge(FileTooLargeRedirectException e) {
        Map<String, String> body = new HashMap<>();
        body.put("message", "File too large. Download PDF instead.");
        body.put("download", e.getDownloadUrl());
        return ResponseEntity.status(300).body(body);
    }
    //exception for not supported format
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedOperation(UnsupportedOperationException ex) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }
    //etc.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred.");
    }
    // exception answer body
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
