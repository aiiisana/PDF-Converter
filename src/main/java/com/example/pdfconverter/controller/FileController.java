package com.example.pdfconverter.controller;

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

@RestController
@RequestMapping("/api")
public class FileController {
    private final ConversionService conversionService;

    public FileController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping("/convert")
    public ResponseEntity<byte[]> convertFile(@RequestParam("file") MultipartFile file) throws Exception {
        byte[] bytes = conversionService.convertToPdf(file);
        String originalName = file.getOriginalFilename();
        String convertedName = (originalName != null ? originalName.replaceAll("\\.[^.]+$", "") : "converted") + "_converted.pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + convertedName)
                .body(bytes);
    }

    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) throws Exception {
        Path path = Paths.get("generated-files", filename).normalize();
        System.out.println("Path to file: " + path.toAbsolutePath());

        if (!Files.exists(path)) {
            System.out.println("File not found: " + path.toAbsolutePath());
            throw new FileNotFoundException("File not found: " + filename);
        }

        byte[] testRead = Files.readAllBytes(path);
        System.out.println("Successfully read " + testRead.length + " bytes from file.");

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