package com.pdfchat.controller.cloudinary;

import com.pdfchat.service.cloudinary.CloudinaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pdf")
public class CloudinaryController {

    @Autowired
    private CloudinaryService cloudinaryService;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("pdf") MultipartFile file) {
        try {
            String publicId = cloudinaryService.uploadPdf(file);

            return ResponseEntity.ok(Map.of(
                    "message", "Upload successful",
                    "filename", file.getOriginalFilename(),
                    "public_id", publicId
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("PDF upload failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    // (IGNORE THIS) fetch the PDF link by public_id and returns the url link
    @PostMapping("/link")
    public ResponseEntity<?> getPdfLinkPost(@RequestBody Map<String, String> request) {
        String publicId = request.get("public_id");

        String url = cloudinaryService.getPdfUrl(publicId);

        return ResponseEntity.ok(Map.of(
                "public_id", publicId,
                "url", url
        ));
    }
}