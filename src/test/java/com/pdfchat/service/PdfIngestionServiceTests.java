package com.pdfchat.service.impl;

import com.pdfchat.entity.DocumentEntity;
import com.pdfchat.model.DocumentStatus;
import com.pdfchat.model.UploadResponse;
import com.pdfchat.repository.DocumentRepository;
import com.pdfchat.service.CloudinaryService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static com.pdfchat.constants.DocumentIngestionConstant.DEFAULT_FILENAME;
import static com.pdfchat.constants.DocumentIngestionConstant.MAX_PDF_LIMIT;
import static com.pdfchat.constants.DocumentIngestionConstant.UPLOAD_DIR;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfIngestionServiceTests {

    private static final String FILE_PARAM_NAME = "file";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String TEST_FILE = "unit-test-policy.pdf";
    private static final String TEST_FILE_WITH_SPACES = "unit-test policy (1).pdf";
    private static final String TEST_FILE_SANITISED = "unit-test_policy__1_.pdf";
    private static final String TEST_FILE_REAL_POLICY = "unit-test-real-policy.pdf";
    private static final String BLANK_FILENAME = "   ";
    private static final String MOCK_PUBLIC_ID = "public-id";
    private static final String MOCK_CLOUDINARY_URL = "https://cloudinary.test/public-id.pdf";
    private static final String REAL_POLICY_PDF = "pdfs/Information-Security-Policy.pdf";
    private static final String SAMPLE_POLICY_TEXT = "This is a sample information security policy for the company.";
    private static final String SAMPLE_SHORT_TEXT = "Some text";
    private static final String OLD_DOCUMENT_TEXT = "Old document text";
    private static final String EMPTY_PDF_TEXT = "";
    private static final String STATUS_SUCCESS = "Success";
    private static final String UPLOAD_LIMIT_MESSAGE = "Upload limit reached";
    private static final String NO_TEXT_MESSAGE = "No extractable text found in PDF";
    private static final String METADATA_SOURCE = "source";
    private static final String METADATA_CLOUDINARY_URL = "cloudinary_url";
    private static final String METADATA_PAGE = "page";
    private static final String POLICY_KEYWORD = "informationsecurity";

    @Mock
    private VectorStore vectorStore;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private DocumentRepository documentRepository;

    @Captor
    private ArgumentCaptor<List<Document>> chunksCaptor;

    @Captor
    private ArgumentCaptor<DocumentEntity> documentCaptor;

    @InjectMocks
    private PdfIngestionServiceImpl pdfIngestionService;

    @AfterEach
    void deleteSavedTestFiles() throws IOException {
        Files.deleteIfExists(Paths.get(UPLOAD_DIR, TEST_FILE));
        Files.deleteIfExists(Paths.get(UPLOAD_DIR, TEST_FILE_SANITISED));
        Files.deleteIfExists(Paths.get(UPLOAD_DIR, TEST_FILE_REAL_POLICY));
        // the default filename is used when a PDF has no name, so the saved copy is removed too
        Files.deleteIfExists(Paths.get(UPLOAD_DIR, DEFAULT_FILENAME));
    }

    /**
     * Stubs the repository and Cloudinary so an upload goes through normally:
     * no documents stored yet, and the PDF is uploaded and given a URL.
     *
     * @param file the file that will be uploaded
     */
    private void givenDocumentCanBeUploaded(MultipartFile file) throws IOException {
        when(documentRepository.count()).thenReturn(0L);
        when(cloudinaryService.uploadPdf(file)).thenReturn(MOCK_PUBLIC_ID);
        when(cloudinaryService.getPdfUrl(MOCK_PUBLIC_ID)).thenReturn(MOCK_CLOUDINARY_URL);
    }

    /**
     * Builds a one page PDF upload with the given filename and text.
     *
     * @param filename the original filename of the upload
     * @param text     the text written on the page, empty gives a blank page
     * @return a mock multipart file holding the generated PDF
     */
    private MockMultipartFile buildPdfFile(String filename, String text) {
        return new MockMultipartFile(FILE_PARAM_NAME, filename, PDF_CONTENT_TYPE, createPdf(text));
    }

    /**
     * Builds a one page PDF in memory, an empty text gives a blank page.
     *
     * @param text the text written on the page
     * @return the PDF as bytes
     */
    private byte[] createPdf(String text) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            if (!text.isEmpty()) {
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("throws IllegalArgumentException when the upload limit is reached")
    void shouldThrowIllegalArgumentExceptionWhenUploadLimitReached() throws IOException {
        MockMultipartFile file = buildPdfFile(TEST_FILE, SAMPLE_SHORT_TEXT);
        when(documentRepository.count()).thenReturn((long) MAX_PDF_LIMIT);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> pdfIngestionService.ingestPdf(file))
                .withMessage(UPLOAD_LIMIT_MESSAGE);

        verify(cloudinaryService, never()).uploadPdf(any());
    }

    @Test
    @DisplayName("returns a success response when the PDF is valid")
    void shouldReturnSuccessResponseWhenPdfIsValid() throws IOException {
        MockMultipartFile file = buildPdfFile(TEST_FILE, SAMPLE_POLICY_TEXT);
        givenDocumentCanBeUploaded(file);

        UploadResponse response = pdfIngestionService.ingestPdf(file);

        assertEquals(STATUS_SUCCESS, response.getStatus());
        assertEquals(TEST_FILE, response.getFile());
        assertTrue(response.getIndexedChunks() > 0);
        verify(vectorStore).add(any());
    }

    @Test
    @DisplayName("marks the saved record as READY when the PDF is valid")
    void shouldMarkRecordAsReadyWhenPdfIsValid() throws IOException {
        MockMultipartFile file = buildPdfFile(TEST_FILE, SAMPLE_POLICY_TEXT);
        givenDocumentCanBeUploaded(file);

        pdfIngestionService.ingestPdf(file);

        // the same record is saved twice, first as PROCESSING and then as READY
        verify(documentRepository, times(2)).save(documentCaptor.capture());
        DocumentEntity savedRecord = documentCaptor.getValue();
        assertEquals(TEST_FILE, savedRecord.getFilename());
        assertEquals(MOCK_PUBLIC_ID, savedRecord.getCloudinaryPublicId());
        assertEquals(MOCK_CLOUDINARY_URL, savedRecord.getCloudinaryUrl());
        assertEquals(DocumentStatus.READY, savedRecord.getStatus());
    }

    @Test
    @DisplayName("sanitises the filename when it contains special characters")
    void shouldSanitiseFilenameWhenItContainsSpecialCharacters() throws IOException {
        MockMultipartFile file = buildPdfFile(TEST_FILE_WITH_SPACES, SAMPLE_POLICY_TEXT);
        givenDocumentCanBeUploaded(file);

        UploadResponse response = pdfIngestionService.ingestPdf(file);

        assertEquals(TEST_FILE_SANITISED, response.getFile());
    }

    @Test
    @DisplayName("uses the default filename when the filename is blank")
    void shouldUseDefaultFilenameWhenFilenameIsBlank() throws IOException {
        // a blank original filename should be replaced with the default one (upload.pdf)
        MockMultipartFile file = buildPdfFile(BLANK_FILENAME, SAMPLE_POLICY_TEXT);
        givenDocumentCanBeUploaded(file);

        UploadResponse response = pdfIngestionService.ingestPdf(file);

        assertEquals(DEFAULT_FILENAME, response.getFile());
    }

    @Test
    @DisplayName("uses the default filename when the filename is null")
    void shouldUseDefaultFilenameWhenFilenameIsNull() throws IOException {
        // MockMultipartFile turns a null name into an empty one, so a plain mock is used to get a real null
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(createPdf(SAMPLE_POLICY_TEXT)));
        givenDocumentCanBeUploaded(file);

        UploadResponse response = pdfIngestionService.ingestPdf(file);

        assertEquals(DEFAULT_FILENAME, response.getFile());
    }

    @Test
    @DisplayName("clears the existing vectors when a new PDF is ingested")
    void shouldClearExistingVectorsWhenIngestingNewPdf() throws IOException {
        MockMultipartFile file = buildPdfFile(TEST_FILE, SAMPLE_POLICY_TEXT);
        Document oldDocument = new Document(OLD_DOCUMENT_TEXT);
        givenDocumentCanBeUploaded(file);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(oldDocument));

        pdfIngestionService.ingestPdf(file);

        verify(vectorStore).delete(List.of(oldDocument.getId()));
    }

    @Test
    @DisplayName("marks the saved record as FAILED when the PDF has no text")
    void shouldMarkRecordAsFailedWhenPdfHasNoText() throws IOException {
        MockMultipartFile file = buildPdfFile(TEST_FILE, EMPTY_PDF_TEXT);
        givenDocumentCanBeUploaded(file);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> pdfIngestionService.ingestPdf(file))
                .withMessage(NO_TEXT_MESSAGE);

        verify(documentRepository, atLeastOnce()).save(documentCaptor.capture());
        assertEquals(DocumentStatus.FAILED, documentCaptor.getValue().getStatus());
        verify(vectorStore, never()).add(any());
    }

    @Test
    @DisplayName("splits the real policy PDF into chunks tagged with their source")
    void shouldSplitIntoTaggedChunksWhenPdfIsRealPolicy() throws IOException {
        // reads the real PDF from src/main/resources/pdfs, uploaded under a test filename so no real upload is overwritten
        byte[] realPdf = new ClassPathResource(REAL_POLICY_PDF).getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile(FILE_PARAM_NAME, TEST_FILE_REAL_POLICY, PDF_CONTENT_TYPE, realPdf);
        givenDocumentCanBeUploaded(file);

        UploadResponse response = pdfIngestionService.ingestPdf(file);

        verify(vectorStore).add(chunksCaptor.capture());
        List<Document> chunks = chunksCaptor.getValue();
        // the policy is 12 pages long so it must produce more than one chunk
        assertTrue(chunks.size() > 1);
        assertEquals(chunks.size(), response.getIndexedChunks());
        // every chunk is tagged with where it came from
        for (Document chunk : chunks) {
            assertEquals(TEST_FILE_REAL_POLICY, chunk.getMetadata().get(METADATA_SOURCE));
            assertEquals(MOCK_CLOUDINARY_URL, chunk.getMetadata().get(METADATA_CLOUDINARY_URL));
            assertNotNull(chunk.getMetadata().get(METADATA_PAGE));
        }
        // the real text of the policy was extracted (spaces, line breaks and odd characters are ignored)
        boolean containsPolicyText = chunks.stream()
                .anyMatch(chunk -> chunk.getText()
                        .toLowerCase()
                        .replaceAll("[^a-z]", "")
                        .contains(POLICY_KEYWORD));
        assertTrue(containsPolicyText,
                "No chunk contained 'information security'. First chunk was: [" + chunks.get(0).getText() + "]");
    }
}