package com.pdfchat.service;

import org.springframework.web.multipart.MultipartFile;

public interface ImageValidationService {
    void validate(MultipartFile file);
    void validateBatch(MultipartFile[] files);
}