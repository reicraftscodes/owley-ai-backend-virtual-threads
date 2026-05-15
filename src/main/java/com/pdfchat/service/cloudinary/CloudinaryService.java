package com.pdfchat.service.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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
public class CloudinaryService {

    @Autowired
    private Cloudinary cloudinary;

    @Value("${cloudinary.pdf-folder}")
    private String pdfFolder;

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    public String uploadPdf(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }

        String originalName = file.getOriginalFilename();

        // Validate that the uploaded file exists and is a PDF
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are allowed");
        }

        String baseName = originalName.substring(0, originalName.lastIndexOf("."));

        String cloudinaryPublicId = baseName + "_" + UUID.randomUUID().toString();

        Map<String, Object> uploadParams = ObjectUtils.asMap(
                // Cloudinary folder (home/pdf-chat)
                "folder", pdfFolder,
                "public_id", cloudinaryPublicId,
                "resource_type", "raw",
                "overwrite", false
        );

        cloudinary.uploader().upload(file.getBytes(), uploadParams);

        log.info("PDF uploaded to Cloudinary with public_id: {}", cloudinaryPublicId);

        return cloudinaryPublicId;
    }

    public String getPdfUrl(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException("Public ID cannot be null or empty");
        }

        // Use cloudName and pdfFolder from configuration
        return "https://res.cloudinary.com/" + cloudName
                + "/raw/upload/" + pdfFolder + "/" + publicId;
    }
}