package com.example.pdfconverter.util;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.io.image.*;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class PdfUtils {
    public static byte[] convertTxtToPdf(MultipartFile file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // we create pdf writer that writes in output stream
        PdfWriter writer = new PdfWriter(outputStream);
        // then we create new pdf and make it a document for adding string
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        // now we get the string form the txt file and write it to the pdf doc
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        document.add(new Paragraph(content));
        // closing document and returning pdf as byte array
        document.close();
        return outputStream.toByteArray();
    }
    public static byte[] convertImageToPdf(MultipartFile file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        //the same as above, but now we get bytes from uploaded image and create image data
        ImageData imageData = ImageDataFactory.create(file.getBytes());
        Image image = new Image(imageData);
        //adding image form image data to pdf doc, closing and returning
        document.add(image);
        document.close();
        return outputStream.toByteArray();
    }
    public static byte[] convertDocxToPdf(MultipartFile file) throws IOException, InterruptedException {
        //there we create temporary files for input
        File tempFileInput = File.createTempFile("input", ".docx");
        //next we save the input file
        file.transferTo(tempFileInput);
        //there we use LibreOffice
        ProcessBuilder builder = new ProcessBuilder(
                "soffice", "--headless", "--convert-to", "pdf", "--outdir",
                tempFileInput.getParent(), tempFileInput.getAbsolutePath()
        );
        //then we start the process and wait
        builder.redirectErrorStream(true);
        Process process = builder.start();
        process.waitFor();
        // libreOffice creates the output file with the same name (but .pdf)
        String baseName = tempFileInput.getName().replaceFirst("[.][^.]+$", "");
        File generatedPdf = new File(tempFileInput.getParent(), baseName + ".pdf");
        //now we finally read generated pdf to byte array
        byte[] pdfBytes = Files.readAllBytes(generatedPdf.toPath());
        //deleting the temporary files now
        tempFileInput.delete();
        generatedPdf.delete();
        //returning pdf as bytes
        return pdfBytes;
    }
}
