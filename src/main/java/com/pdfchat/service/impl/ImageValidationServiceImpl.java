package com.pdfchat.service.impl;

import com.pdfchat.service.ImageValidationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageValidationServiceImpl implements ImageValidationService {

    @Override
    public void validate(MultipartFile file) {

        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw new IllegalArgumentException("Invalid image");
        }

        String name = file.getOriginalFilename().toLowerCase();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();

        // Accept only PNG, JPG, JPEG, WEBP
        if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")
                || contentType.equals("image/png")
                || contentType.equals("image/jpg")
                || contentType.equals("image/jpeg")
                || contentType.equals("image/webp"))) {
            throw new IllegalArgumentException("Only PNG, JPG, JPEG, WEBP images allowed");
        }
    }

    @Override
    public void validateBatch(MultipartFile[] files) {

        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No images provided");
        }

        if (files.length > 5) {
            throw new IllegalArgumentException("Max 5 images allowed per request");
        }

        for (MultipartFile file : files) {
            validate(file);
        }
    }
}