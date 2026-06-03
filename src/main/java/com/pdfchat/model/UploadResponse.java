package com.pdfchat.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UploadResponse {
    private String status;
    private int indexedChunks;
    private String file;
    private String cloudinaryUrl;
    private LocalDateTime uploadTime;
}
