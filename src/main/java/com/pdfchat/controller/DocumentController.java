package com.pdfchat.controller;

import com.pdfchat.model.AskRequest;
import com.pdfchat.model.AskResponse;
import com.pdfchat.model.UploadResponse;
import com.pdfchat.service.MultiImageIngestionService;
import com.pdfchat.service.PdfIngestionService;
import com.pdfchat.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static com.pdfchat.constants.DocumentIngestionConstant.QUESTION_REQUIRED_MESSAGE;


@Slf4j
@RestController
@RequestMapping("/api/documents")
@CrossOrigin("http://localhost:5000/")
public class DocumentController {

    @Autowired
    private PdfIngestionService pdfIngestionService;

    @Autowired
    private MultiImageIngestionService multiImageIngestionService;

    @Autowired
    private RagService ragService;

    @PostMapping("/upload/pdf")
    public ResponseEntity<UploadResponse> uploadPdf(@RequestParam("pdf") MultipartFile file) throws IOException {
        UploadResponse response = pdfIngestionService.ingestPdf(file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload/images")
    public ResponseEntity<UploadResponse> uploadImages(@RequestParam("files") MultipartFile[] files) throws IOException {
        UploadResponse response = multiImageIngestionService.ingestImages(files);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@RequestBody AskRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException(QUESTION_REQUIRED_MESSAGE);
        }

        AskResponse response = ragService.ask(request.getQuestion().trim());
        return ResponseEntity.ok(response);
    }
}