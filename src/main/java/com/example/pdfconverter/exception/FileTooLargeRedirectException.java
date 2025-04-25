package com.example.pdfconverter.exception;

public class FileTooLargeRedirectException extends RuntimeException {
    private final String downloadUrl;

    public FileTooLargeRedirectException(String downloadUrl) {
        super("File too large");
        this.downloadUrl = downloadUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}