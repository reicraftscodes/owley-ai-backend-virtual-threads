package com.pdfchat.service;

import com.pdfchat.model.dto.UploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;


/**
 * Handles PDF upload, text extraction, chunking, and vector store ingestion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfIngestionService {

    private static final String UPLOAD_DIR = System.getProperty("user.home") + "/pdf-chat-uploads";

    private final VectorStore vectorStore;

    public UploadResponse ingestPdf(MultipartFile file) throws IOException {

        // Clear all existing vectors from Pinecone before ingesting new PDF. Without this, old document chunks (e.g. ethics.pdf) remain in the index
        // and get mixed with the new document during similarity search causing answers to bleed across different uploaded files.
        clearExistingVectors();

        Path uploadPath = Paths.get(UPLOAD_DIR);
        Files.createDirectories(uploadPath);

        String filename = sanitizeFilename(file.getOriginalFilename());

        File savedFile = uploadPath.resolve(filename).toFile();
        Files.copy(file.getInputStream(), savedFile.toPath(), REPLACE_EXISTING);

        log.info("Saved PDF: {}", savedFile.getAbsolutePath());

        //Extract text from PDF using Spring AI's PagePdfDocumentReader equivalent to PyPDF2's PdfReader in python reads page-by-page
        PagePdfDocumentReader pdfReader = getPagePdfDocumentReader(savedFile);

        List<Document> rawDocs = pdfReader.get();

        if (rawDocs.isEmpty()) {
            throw new IllegalArgumentException("No extractable text found in PDF");
        }

        log.info("Extracted {} pages from PDF", rawDocs.size());

        // Enrich metadata with source filename (mirrors metadata={"source": pdf_file.filename})
        List<Document> taggedDocs = rawDocs.stream().map(doc -> new Document(
                        doc.getText(),
                        Map.of("source", filename, "page", doc.getMetadata().getOrDefault("page_number", "?"))
                ))
                .collect(Collectors.toList());

        // Split into overlapping chunks using TokenTextSplitter
        // TokenTextSplitter works on tokens (more accurate than char-count)
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(512)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();

        List<Document> chunks = splitter.apply(taggedDocs);
        log.info("Split into {} chunks", chunks.size());

        // Add to Pinecone via Spring AI VectorStore
        // this automatically calls OpenAI text-embedding-3-small to embed each chunk and upserts vectors + metadata into Pinecone
        vectorStore.add(chunks);

        log.info("Successfully indexed {} chunks for file: {}", chunks.size(), filename);

        return UploadResponse.builder()
                .status("success")
                .indexedChunks(chunks.size())
                .file(filename)
                .build();
    }

    private static PagePdfDocumentReader getPagePdfDocumentReader(File savedFile) {
        PdfDocumentReaderConfig readerConfig = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPageBottomMargin(0)
                .build();

        return new PagePdfDocumentReader(
                new FileSystemResource(savedFile),
                readerConfig
        );
    }

    private void clearExistingVectors() {
        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(" ")
                        .topK(10000)
                        .build()
        );

        if (!existing.isEmpty()) {
            List<String> ids = existing.stream()
                    .map(Document::getId)
                    .collect(Collectors.toList());
            vectorStore.delete(ids);
            log.info("Cleared {} existing vectors", ids.size());
        }
    }

    /**
     * Sanitise filename - keeps alphanumeric, dots, dashes, underscores only.
     */
    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "upload.pdf";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
