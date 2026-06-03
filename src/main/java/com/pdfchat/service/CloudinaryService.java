package com.pdfchat.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface CloudinaryService {

    String uploadPdf(MultipartFile file) throws IOException;
    String getPdfUrl(String publicId);
    String uploadImage(MultipartFile file) throws IOException;
    String getImageUrl(String publicId);
}