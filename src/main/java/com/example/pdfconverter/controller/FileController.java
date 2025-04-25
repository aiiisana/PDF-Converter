package com.example.pdfconverter.controller;

import com.example.pdfconverter.exception.FileTooLargeRedirectException;
import com.example.pdfconverter.service.ConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {

    private final ConversionService conversionService;

    @Operation(summary = "Convert a file to PDF")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully converted to PDF"),
            @ApiResponse(responseCode = "302", description = "File too large, redirect to download link"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws Exception {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String originalName = file.getOriginalFilename();
        String convertedName = (originalName != null ? originalName.replaceAll("\\.[^.]+$", "") : "converted") + "_converted.pdf";

        try {
            byte[] pdfBytes = conversionService.convertToPdf(file);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + convertedName)
                    .body(pdfBytes);

        } catch (FileTooLargeRedirectException ex) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "File too large. Download it from the provided link.");
            response.put("downloadLink", "/api/files" + ex.getDownloadUrl());
            return ResponseEntity.status(302).body(response);
        }
    }

    @Operation(summary = "Download a previously generated PDF file")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File downloaded"),
            @ApiResponse(responseCode = "404", description = "File not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("files/{filename:.+}")
    public ResponseEntity<?> downloadFile(
            @PathVariable String filename,
            Authentication authentication
    ) throws Exception {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        Path path = Paths.get("generated-files", filename).normalize();
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + filename);
        }

        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("File not readable: " + filename);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
