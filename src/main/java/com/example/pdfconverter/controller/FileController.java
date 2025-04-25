package com.example.pdfconverter.controller;

import com.example.pdfconverter.exception.FileTooLargeRedirectException;
import com.example.pdfconverter.service.ConversionService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FileController {
    private final ConversionService conversionService;

    public FileController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

@PostMapping("/convert")
public ResponseEntity<?> convertFile(@RequestParam("file") MultipartFile file) throws Exception {
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
        response.put("message", "File too large. Download using the link above");
        response.put("download", "/api" + ex.getDownloadUrl());
        return ResponseEntity.status(302).body(response);
    }
}


    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) throws Exception {
        Path path = Paths.get("generated-files", filename).normalize();
        System.out.println("Path to file: " + path.toAbsolutePath());

        if (!Files.exists(path)) {
            System.out.println("File not found: " + path.toAbsolutePath());
            throw new FileNotFoundException("File not found: " + filename);
        }
        Resource fileResource = new UrlResource(path.toUri());

        if (!fileResource.exists() || !fileResource.isReadable()) {
            System.out.println("File resource not readable: " + path.toAbsolutePath());
            throw new RuntimeException("File resource not readable: " + filename);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(fileResource);
    }
}