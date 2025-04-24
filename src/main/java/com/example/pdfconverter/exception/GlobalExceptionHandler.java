package com.example.pdfconverter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
@RestControllerAdvice
public class GlobalExceptionHandler {
    //exception for too large files
    @ExceptionHandler(FileTooLargeRedirectException.class)
    public ResponseEntity<Map<String, String>> handleTooLarge(FileTooLargeRedirectException e) {
        Map<String, String> body = new HashMap<>();
        body.put("message", "File too large. Download PDF:");
        body.put("download", e.getDownloadUrl());
        return ResponseEntity.status(302).body(body);
    }
    @ExceptionHandler({MalformedURLException.class, FileNotFoundException.class, IOException.class})
    public ResponseEntity<Void> handleFileDownloadExceptions(Exception ex) {
        System.out.println("Handled file download exception: " + ex.getMessage());
        return ResponseEntity.ok().build(); // Просто 200 OK без тела
    }
    //exception for not supported format
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedOperation(UnsupportedOperationException ex) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResourceFound(NoResourceFoundException ex) {
        System.out.println("No resource found, but returning 200 OK: " + ex.getMessage());
        return ResponseEntity.ok().build();
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
