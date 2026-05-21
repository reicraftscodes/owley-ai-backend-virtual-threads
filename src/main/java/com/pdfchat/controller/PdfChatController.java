package com.pdfchat.controller;

import com.pdfchat.model.dto.AskRequest;
import com.pdfchat.model.dto.AskResponse;
import com.pdfchat.model.dto.UploadResponse;
import com.pdfchat.service.PdfIngestionService;
import com.pdfchat.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@CrossOrigin
@RequiredArgsConstructor
public class PdfChatController {

    private final PdfIngestionService pdfIngestionService;
    private final RagService ragService;

    @GetMapping("/")
    public String home() {
        return "Finley AI is running!";
    }

    @PostMapping("/upload_pdf")
    public ResponseEntity<?> uploadPdf(@RequestParam("pdf") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Upload PDF using field name 'pdf'"));
        }
        try {
            UploadResponse response = pdfIngestionService.ingestPdf(file);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("PDF ingestion failed", e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Ingestion failed: " + e.getMessage()));
        }
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody AskRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Question is required"));
        }
        try {
            AskResponse response = ragService.ask(request.getQuestion().trim());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RAG query failed", e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Query failed: " + e.getMessage()));
        }
    }


    record ErrorResponse(String error) {
    }
}
