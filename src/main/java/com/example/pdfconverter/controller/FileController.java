package com.example.pdfconverter.controller;

import com.example.pdfconverter.service.ConversionService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=" + convertedName)
        .body(bytes);
    }
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path path = Paths.get("generated-files", filename).normalize();
            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }

            Resource fileResource = new UrlResource(path.toUri());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(fileResource);

        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build(); // или throw new RuntimeException("Invalid file URL", e);
        }
    }
}
