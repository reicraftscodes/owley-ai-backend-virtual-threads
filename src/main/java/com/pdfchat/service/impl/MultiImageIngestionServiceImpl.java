package com.pdfchat.service.impl;

import com.pdfchat.entity.DocumentEntity;
import com.pdfchat.model.DocumentStatus;
import com.pdfchat.model.DocumentType;
import com.pdfchat.model.UploadResponse;
import com.pdfchat.repository.ImagesDocumentRepository;
import com.pdfchat.service.CloudinaryService;
import com.pdfchat.service.ImageValidationService;
import com.pdfchat.service.MultiImageIngestionService;
import com.pdfchat.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.pdfchat.constants.DocumentIngestionConstant.*;

@Slf4j
@Service
public class MultiImageIngestionServiceImpl implements MultiImageIngestionService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private ImagesDocumentRepository imagesDocumentRepository;

    @Autowired
    private OcrService ocrService;

    @Autowired
    private ImageValidationService imageValidationService;

    @Override
    public UploadResponse ingestImages(MultipartFile[] files) throws IOException {

        // validate batch request — max 5 images, not null, not empty
        imageValidationService.validateBatch(files);

        LocalDateTime uploadTime = LocalDateTime.now();
        List<Document> allChunks = new ArrayList<>();

        String filename = null;
        String cloudinaryUrl = null;
        String cloudinaryPublicId = null;

        TokenTextSplitter splitter = buildTextSplitter();

        // process each image independently
        for (MultipartFile file : files) {

            // validate individual image
            imageValidationService.validate(file);

            // sanitize filename
            filename = sanitizeFilename(file.getOriginalFilename());

            // upload to Cloudinary and get URL
            cloudinaryPublicId = cloudinaryService.uploadImage(file);
            cloudinaryUrl = cloudinaryService.getImageUrl(cloudinaryPublicId);
            log.info(CLOUDINARY_URL_RECEIVED, cloudinaryUrl);

            // extract OCR text per image Spring AI Media
            String ocrText = ocrService.extractText(file);

            // skip image if no text extracted
            if (ocrText == null || ocrText.isBlank()) {
                log.warn("No OCR text extracted from: {}", filename);
                continue;
            }

            // trim per image to stay within safe token range
            ocrText = trimText(ocrText, 4000);

            // wrap OCR text in a Spring AI Document with metadata
            Document doc = new Document(ocrText, Map.of(
                    "source", filename,
                    "cloudinary_url", cloudinaryUrl,
                    "type", "IMAGE"
            ));

            // chunk this image's text independently — prevents cross-image token bleed
            allChunks.addAll(splitter.apply(List.of(doc)));
        }

        // create DB record with PROCESSING status
        DocumentEntity docRecord = createDocumentRecord(
                filename, cloudinaryPublicId, cloudinaryUrl, uploadTime
        );

        // clear previous vectors
        clearExistingVectors();

        // fail if no usable text from any image
        if (allChunks.isEmpty()) {
            docRecord.setStatus(DocumentStatus.FAILED);
            imagesDocumentRepository.save(docRecord);
            throw new IllegalArgumentException(NO_TEXT_FOUND_MESSAGE);
        }

        // store chunks in vector store
        vectorStore.add(allChunks);
        log.info(CHUNKS_INDEXED, allChunks.size(), filename);

        // mark complete
        docRecord.setStatus(DocumentStatus.READY);
        imagesDocumentRepository.save(docRecord);

        return UploadResponse.builder()
                .status(UPLOAD_RESULT_SUCCESS)
                .indexedChunks(allChunks.size())
                .file(filename)
//                .cloudinaryUrl(cloudinaryUrl)
                .uploadTime(uploadTime)
                .build();
    }

    // build text splitter
    private static TokenTextSplitter buildTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(512)
                .withMinChunkSizeChars(50)
                .build();
    }

    // create DB record
    private DocumentEntity createDocumentRecord(String filename, String cloudinaryPublicId, String cloudinaryUrl, LocalDateTime uploadTime) {
        DocumentEntity doc = new DocumentEntity();
        doc.setFilename(filename);
        doc.setType(DocumentType.IMAGE);
        doc.setContentType("image");
        doc.setCloudinaryPublicId(cloudinaryPublicId);
        doc.setCloudinaryUrl(cloudinaryUrl);
        doc.setStatus(DocumentStatus.PROCESSING);
        doc.setUploadTime(uploadTime);

        return imagesDocumentRepository.save(doc);
    }

    // clear vectors
    private void clearExistingVectors() {
        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(" ")
                        .topK(1000)
                        .build()
        );

        if (!existing.isEmpty()) {
            vectorStore.delete(existing.stream().map(Document::getId).toList());
            log.info(VECTORS_CLEARED, existing.size());
        }
    }

    // sanitize filename
    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT_FILENAME;
        }
        return name.replaceAll(FILENAME_SANITIZE_REGEX, FILENAME_REPLACEMENT);
    }

    // trim OCR text safely
    private String trimText(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }

        int endIndex = Math.min(text.length(), maxChars);

        return text.substring(0, endIndex);
    }
}