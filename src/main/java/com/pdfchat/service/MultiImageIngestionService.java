package com.pdfchat.service;

import com.pdfchat.model.UploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface MultiImageIngestionService {
    UploadResponse ingestImages(MultipartFile[] file) throws IOException;
}