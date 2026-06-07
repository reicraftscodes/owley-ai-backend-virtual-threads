package com.pdfchat.service.impl;

import com.pdfchat.model.UploadResponse;
import com.pdfchat.entity.DocumentEntity;
import com.pdfchat.model.DocumentStatus;
import com.pdfchat.repository.DocumentRepository;
import com.pdfchat.service.CloudinaryService;
import com.pdfchat.service.PdfIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.pdfchat.constants.DocumentIngestionConstant.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Slf4j
@Service
public class PdfIngestionServiceImpl implements PdfIngestionService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private DocumentRepository documentRepository;

    @Override
    public UploadResponse ingestPdf(MultipartFile file) throws IOException {

        // query the database to get the total number of uploaded PDFs
        long existingCount = documentRepository.count();
        log.info(PDF_COUNT_CHECK, existingCount);

        // if the count has reached the limit, reject the upload and notify the user
        if (existingCount >= MAX_PDF_LIMIT) {
            log.warn(UPLOAD_DESCRIPTION_REJECTED, MAX_PDF_LIMIT);
            throw new IllegalArgumentException("Upload limit reached");
        }

        // upload allowed, send the PDF to Cloudinary for secure cloud storage
        log.info(UPLOAD_DESCRIPTION_ALLOWED);
        String cloudinaryPublicId = cloudinaryService.uploadPdf(file);

        // construct the full Cloudinary URL from the returned public_id
        String cloudinaryUrl = cloudinaryService.getPdfUrl(cloudinaryPublicId);
        log.info(CLOUDINARY_URL_RECEIVED, cloudinaryUrl);

        // record the upload in the database with status PROCESSING, Cloudinary URL and upload time
        LocalDateTime uploadTime = LocalDateTime.now(ZoneId.systemDefault());
        String filename = sanitizeFilename(file.getOriginalFilename());

        // create and persist a new document record with status PROCESSING
        DocumentEntity docRecord = createDocumentRecord(filename, cloudinaryPublicId, cloudinaryUrl, uploadTime);
        log.info(DOCUMENT_RECORD_SAVED, filename, uploadTime);

        // clear any previously indexed vectors from Pinecone to avoid cross-document contamination
        clearExistingVectors();

        // save the PDF locally as a temporary file for text extraction
        File savedFile = saveFileLocally(file, filename);

        // extract raw text from each page of the PDF using Spring AI's PagePdfDocumentReader
        PagePdfDocumentReader pdfReader = getPagePdfDocumentReader(savedFile);
        List<Document> rawDocs = pdfReader.get();

        // if no text was extracted, mark the record as FAILED and reject the upload
        if (rawDocs.isEmpty()) {
            docRecord.setStatus(DocumentStatus.FAILED);
            documentRepository.save(docRecord);
            throw new IllegalArgumentException(NO_TEXT_FOUND_MESSAGE);
        }

        // enrich each document chunk with metadata — source filename, page number and Cloudinary URL
        List<Document> taggedDocs = tagDocumentsWithMetadata(rawDocs, filename, cloudinaryUrl);

        // build the text splitter to divide documents into smaller overlapping chunks
        TokenTextSplitter splitter = buildTextSplitter();

        // embed each chunk and upsert into Pinecone vector store via Spring AI
        List<Document> chunks = splitter.apply(taggedDocs);
        vectorStore.add(chunks);
        log.info(CHUNKS_INDEXED, chunks.size(), filename);

        // update the document record status to READY once ingestion is complete
        docRecord.setStatus(DocumentStatus.READY);
        documentRepository.save(docRecord);

        return UploadResponse.builder()
                .status(UPLOAD_RESULT_SUCCESS)
                .indexedChunks(chunks.size())
                .file(filename)
//                .cloudinaryUrl(cloudinaryUrl)
                .uploadTime(uploadTime)
                .build();
    }

    // build and return a TokenTextSplitter configured for optimal chunk sizes for RAG
    private static TokenTextSplitter buildTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(512)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
    }

    // enrich raw document pages with source filename, page number and Cloudinary URL as metadata
    private static List<Document> tagDocumentsWithMetadata(List<Document> rawDocs, String filename, String cloudinaryUrl) {
        List<Document> list = new ArrayList<>();
        for (Document doc : rawDocs) {
            Document document = new Document(doc.getText(),
                    Map.of("source", filename,
                            "page", doc.getMetadata().getOrDefault("page_number", "?"),
                            "cloudinary_url", cloudinaryUrl)
            );
            list.add(document);
        }
        return list;
    }

    // create a new PdfDocument record, set initial status to PROCESSING and persist to the database
    private DocumentEntity createDocumentRecord(String filename, String cloudinaryPublicId, String cloudinaryUrl, LocalDateTime uploadTime) {
        DocumentEntity docRecord = new DocumentEntity();
        docRecord.setFilename(filename);
        docRecord.setCloudinaryPublicId(cloudinaryPublicId);
        docRecord.setCloudinaryUrl(cloudinaryUrl);
        docRecord.setStatus(DocumentStatus.PROCESSING);
        docRecord.setUploadTime(uploadTime);
        documentRepository.save(docRecord);
        return docRecord;
    }

    // save the uploaded PDF to a local temp directory for text extraction
    private static File saveFileLocally(MultipartFile file, String filename) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        Files.createDirectories(uploadPath);
        File savedFile = uploadPath.resolve(filename).toFile();
        Files.copy(file.getInputStream(), savedFile.toPath(), REPLACE_EXISTING);
        return savedFile;
    }

    // configure the PDF reader with no margins to extract all text from each page
    private static PagePdfDocumentReader getPagePdfDocumentReader(File savedFile) {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPageBottomMargin(0)
                .build();
        return new PagePdfDocumentReader(new FileSystemResource(savedFile), config);
    }

    // fetch all existing vectors from Pinecone and delete them before ingesting a new PDF
    // this prevents answers from bleeding across different uploaded documents
    private void clearExistingVectors() {
        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder().query(" ").topK(MAX_VECTOR_CLEAR_LIMIT).build()
        );
        if (!existing.isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (Document document : existing) {
                String id = document.getId();
                ids.add(id);
            }
            vectorStore.delete(ids);
            log.info(VECTORS_CLEARED, ids.size());
        }
    }

    // sanitize the filename to only allow safe characters — removes spaces and special characters
    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT_FILENAME;
        }
        return name.replaceAll(FILENAME_SANITIZE_REGEX, FILENAME_REPLACEMENT);
    }
}