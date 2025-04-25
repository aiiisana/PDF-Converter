package com.example.pdfconverter.service;

import com.example.pdfconverter.exception.FileTooLargeRedirectException;
import com.example.pdfconverter.utils.PdfUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ConversionServiceImplementation implements ConversionService {
    private static final long max_file_size = 5 * 1024 * 1024;
    @Value("${pdf.output-dir}")
    private String outputDir;
    @Override
    public byte[] convertToPdf(MultipartFile file) throws Exception {
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        if (contentType == null || originalFilename == null) {
            throw new IllegalArgumentException("Invalid file or MIME type.");
        }
        if (file.getSize() > max_file_size) {
            return handleLargeFileConversion(file, contentType, originalFilename);
        }

        return autoConvertBasedOnType(file, contentType, originalFilename);
    }

    private byte[] autoConvertBasedOnType(MultipartFile file, String contentType, String originalFilename) throws Exception {
        if (contentType.equals("text/plain") || originalFilename.endsWith(".txt")) {
            return PdfUtils.convertTxtToPdf(file);
        } else if ((contentType.equals("image/jpeg") || contentType.equals("image/png")) ||
                originalFilename.endsWith(".jpg") || originalFilename.endsWith(".png")) {
            return PdfUtils.convertImageToPdf(file);
        } else if (contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                originalFilename.endsWith(".docx")) {
            return PdfUtils.convertDocxToPdf(file);
        } else {
            throw new UnsupportedOperationException("Unsupported MIME type or file extension: " + contentType);
        }
    }
    private byte[] handleLargeFileConversion(MultipartFile file, String contentType, String originalFilename) throws Exception {

        byte[] pdfBytes = autoConvertBasedOnType(file, contentType, originalFilename);

        String outputFileName = originalFilename.replaceAll("\\.[^.]+$", "") + "_converted.pdf";
        Path outputPath = Paths.get(outputDir).resolve(outputFileName);
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, pdfBytes);

        String downloadUrl = "/files/" + outputFileName;
        System.out.println("Handling large file: " + outputPath.toAbsolutePath());
        throw new FileTooLargeRedirectException(downloadUrl);

    }
}