package com.pdfchat.service.impl;

import com.cloudinary.Cloudinary;
import com.pdfchat.service.CloudinaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class CloudinaryServiceImpl implements CloudinaryService {

    @Autowired
    private Cloudinary cloudinary;

    @Value("${cloudinary.pdf-folder}")
    private String pdfFolder;

    @Value("${cloudinary.image-folder}")
    private String imageFolder;

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Override
    public String uploadPdf(MultipartFile file) throws IOException {
        validateFile(file, "pdf");
        String publicId = generatePublicId(file);
        Map<String, Object> uploadParams = Map.of(
                "folder", pdfFolder,
                "public_id", publicId,
                "resource_type", "raw",
                "type", "authenticated",
                "overwrite", false
        );
        cloudinary.uploader().upload(file.getBytes(), uploadParams);
        log.info("PDF uploaded: {}", publicId);
        return publicId;
    }

    @Override
    public String getPdfUrl(String publicId) {
        return "https://res.cloudinary.com/" + cloudName + "/raw/upload/" + pdfFolder + "/" + publicId;
    }

    private void validateImage(MultipartFile file) {

        String name = file.getOriginalFilename();

        if (file.isEmpty() || name == null) {
            throw new IllegalArgumentException("Invalid image file");
        }

        String lower = name.toLowerCase();

        if (!(lower.endsWith(".png") ||
                lower.endsWith(".jpg") ||
                lower.endsWith(".webp") ||
                lower.endsWith(".jpeg"))) {
            throw new IllegalArgumentException("Only PNG, JPG, JPEG, WEBP allowed");
        }
    }

    @Override
    public String uploadImage(MultipartFile file) throws IOException {
        validateImage(file);

        String publicId = generatePublicId(file);
        Map<String, Object> uploadParams = Map.of(
                "folder", imageFolder,
                "public_id", publicId,
                "resource_type", "image",
                "type", "authenticated",
                "overwrite", false
        );

        cloudinary.uploader().upload(file.getBytes(), uploadParams);
        log.info("Image uploaded: {}", publicId);
        return publicId;
    }

    @Override
    public String getImageUrl(String publicId) {
        return "https://res.cloudinary.com/" + cloudName + "/image/upload/" + imageFolder + "/" + publicId;
    }

    private static void validateFile(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(type)) {
            throw new IllegalArgumentException("Only " + type + " files allowed");
        }
    }



    private static String generatePublicId(MultipartFile file) {
        String name = file.getOriginalFilename();
        String baseName = name.substring(0, name.lastIndexOf("."));
        return baseName + "_" + UUID.randomUUID();
    }
}