package com.example.pdfconverter.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
@Service
public interface ConversionService {
    byte[] convertToPdf(MultipartFile file) throws Exception;
}
