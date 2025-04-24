package com.example.pdfconverter.service;

import com.example.pdfconverter.util.PdfUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ConversionServiceImplementation implements ConversionService {
    private static final long max_file_size = 5 * 1024 * 1024;
    @Value("${pdf.output-dir}")
    private String outputDir;
   @Override
   public byte[] convertToPdf(MultipartFile file) throws Exception {
       if(file.getSize() > max_file_size) {
          throw new IllegalArgumentException("File is too large. Maximum allowed size is 5 MB.");
       }
       String contentType = file.getContentType();
       if(contentType == null) {
           throw new IllegalArgumentException("Invalid file format!");
       }
       if(contentType.equals("text/plain")) {
           return PdfUtils.convertTxtToPdf(file);
       } else if(contentType.equals("image/png") || contentType.equals("image/jpeg")) {
           return PdfUtils.convertImageToPdf(file);
       } else if (contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
           return PdfUtils.convertDocxToPdf(file);
       } else {
           throw new UnsupportedOperationException("Unsupported MIME type: " + contentType);
       }
   }
    private String handleLargeFileConversion(MultipartFile file) throws Exception {
        byte[] pdfBytes = PdfUtils.convertDocxToPdf(file); // или другой формат
        String outputFileName = UUID.randomUUID() + "_converted.pdf";
        Path outputPath = Paths.get(outputDir, outputFileName);
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, pdfBytes);
        return "/files/" + outputFileName;
    }
}
