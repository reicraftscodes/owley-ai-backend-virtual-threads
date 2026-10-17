package com.pdfchat.service;

import com.pdfchat.entity.DocumentEntity;
import com.pdfchat.model.DocumentStatus;
import com.pdfchat.model.UploadResponse;
import com.pdfchat.repository.DocumentRepository;
import com.pdfchat.service.impl.MultiImageIngestionServiceImpl;
import org.junit.jupiter.api.Assertions;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static com.pdfchat.constants.DocumentIngestionConstant.DEFAULT_FILENAME;
import static com.pdfchat.constants.DocumentIngestionConstant.NO_TEXT_FOUND_MESSAGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MultiImageIngestionServiceTests {

    private static final String FAKE_PUBLIC_ID = "image-public-id";
    private static final String FAKE_CLOUDINARY_URL = "https://cloudinary.test/image-public-id.png";
    private static final String FAKE_OCR_TEXT = "This is a sample scanned page about the company password policy.";

    @Mock
    private VectorStore vectorStore;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private OcrService ocrService;

    @Mock
    private ImageValidationService imageValidationService;

    @Captor
    private ArgumentCaptor<List<Document>> chunksCaptor;

    @InjectMocks
    private MultiImageIngestionServiceImpl multiImageIngestionService;

    @Test
    public void ingestImagesWithFailedBatchValidation() throws IOException {
        MultipartFile[] images = {createImage("one.png")};
        doThrow(new IllegalArgumentException("No images provided")).when(imageValidationService).validateBatch(images);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            multiImageIngestionService.ingestImages(images);
        });

        Assertions.assertEquals("No images provided", thrown.getMessage());
        verify(cloudinaryService, never()).uploadImage(any());
    }

    @Test
    public void ingestImagesWithFailedImageValidation() throws IOException {
        MockMultipartFile image = createImage("notes.txt");
        doThrow(new IllegalArgumentException("Only PNG, JPG, JPEG, WEBP images allowed")).when(imageValidationService).validate(image);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            multiImageIngestionService.ingestImages(new MultipartFile[]{image});
        });

        Assertions.assertEquals("Only PNG, JPG, JPEG, WEBP images allowed", thrown.getMessage());
        verify(cloudinaryService, never()).uploadImage(any());
        verify(documentRepository, never()).save(any());
    }

    @Test
    public void validImageReturnsSuccessResponse() throws IOException {
        MockMultipartFile image = createImage("scan.png");
        stubCloudinary(image);
        when(ocrService.extractText(image)).thenReturn(FAKE_OCR_TEXT);
        stubSaveReturnsSameRecord();

        UploadResponse response = multiImageIngestionService.ingestImages(new MultipartFile[]{image});

        Assertions.assertEquals("Success", response.getStatus());
        Assertions.assertEquals("scan.png", response.getFile());
        Assertions.assertEquals(1, response.getIndexedChunks());
        Assertions.assertNotNull(response.getUploadTime());
        verify(imageValidationService).validate(image);
        verify(vectorStore).add(any());
    }

    @Test
    public void validImageRecordEndsAsReady() throws IOException {
        MockMultipartFile image = createImage("scan.png");
        stubCloudinary(image);
        when(ocrService.extractText(image)).thenReturn(FAKE_OCR_TEXT);
        stubSaveReturnsSameRecord();

        multiImageIngestionService.ingestImages(new MultipartFile[]{image});

        // the same record is saved twice, first as PROCESSING and then as READY
        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository, times(2)).save(captor.capture());
        DocumentEntity savedRecord = captor.getValue();
        Assertions.assertEquals("scan.png", savedRecord.getFilename());
        Assertions.assertEquals(FAKE_PUBLIC_ID, savedRecord.getCloudinaryPublicId());
        Assertions.assertEquals(FAKE_CLOUDINARY_URL, savedRecord.getCloudinaryUrl());
        Assertions.assertEquals(DocumentStatus.READY, savedRecord.getStatus());
    }

    @Test
    public void chunksAreTaggedWithSourceUrlAndImageType() throws IOException {
        MockMultipartFile image = createImage("scan.png");
        stubCloudinary(image);
        when(ocrService.extractText(image)).thenReturn(FAKE_OCR_TEXT);
        stubSaveReturnsSameRecord();

        multiImageIngestionService.ingestImages(new MultipartFile[]{image});

        verify(vectorStore).add(chunksCaptor.capture());
        Document chunk = chunksCaptor.getValue().get(0);
        Assertions.assertEquals(FAKE_OCR_TEXT, chunk.getText());
        Assertions.assertEquals("scan.png", chunk.getMetadata().get("source"));
        Assertions.assertEquals(FAKE_CLOUDINARY_URL, chunk.getMetadata().get("cloudinary_url"));
        Assertions.assertEquals("IMAGE", chunk.getMetadata().get("type"));
    }

    @Test
    public void severalImagesAreAllIndexedAndLastFilenameIsReturned() throws IOException {
        MockMultipartFile first = createImage("first image.png");
        MockMultipartFile second = createImage("second image.png");
        stubCloudinary(first);
        stubCloudinary(second);
        when(ocrService.extractText(first)).thenReturn(FAKE_OCR_TEXT);
        when(ocrService.extractText(second)).thenReturn(FAKE_OCR_TEXT);
        stubSaveReturnsSameRecord();

        UploadResponse response = multiImageIngestionService.ingestImages(new MultipartFile[]{first, second});

        // one chunk for each image, and spaces in the name are replaced with underscores
        Assertions.assertEquals(2, response.getIndexedChunks());
        Assertions.assertEquals("second_image.png", response.getFile());
    }

    @Test
    public void imageWithNoTextIsSkipped() throws IOException {
        MockMultipartFile blankImage = createImage("blank.png");
        MockMultipartFile textImage = createImage("text.png");
        stubCloudinary(blankImage);
        stubCloudinary(textImage);
        when(ocrService.extractText(blankImage)).thenReturn("   ");
        when(ocrService.extractText(textImage)).thenReturn(FAKE_OCR_TEXT);
        stubSaveReturnsSameRecord();

        UploadResponse response = multiImageIngestionService.ingestImages(new MultipartFile[]{blankImage, textImage});

        // only the image with text is indexed
        Assertions.assertEquals(1, response.getIndexedChunks());
    }

    @Test
    public void longOcrTextIsTrimmedTo4000Characters() throws IOException {
        MockMultipartFile image = createImage("long.png");
        stubCloudinary(image);
        // 5000 characters of text, more than the 4000 limit
        when(ocrService.extractText(image)).thenReturn("word ".repeat(1000));
        stubSaveReturnsSameRecord();

        multiImageIngestionService.ingestImages(new MultipartFile[]{image});

        verify(vectorStore).add(chunksCaptor.capture());
        int totalLength = chunksCaptor.getValue().stream().mapToInt(chunk -> chunk.getText().length()).sum();
        Assertions.assertTrue(totalLength <= 4000);
    }

    @Test
    public void imagesWithNoTextMarkRecordAsFailed() throws IOException {
        MockMultipartFile blankImage = createImage("blank.png");
        MockMultipartFile nullImage = createImage("null.png");
        stubCloudinary(blankImage);
        stubCloudinary(nullImage);
        when(ocrService.extractText(blankImage)).thenReturn("");
        when(ocrService.extractText(nullImage)).thenReturn(null);
        stubSaveReturnsSameRecord();

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            multiImageIngestionService.ingestImages(new MultipartFile[]{blankImage, nullImage});
        });

        Assertions.assertEquals(NO_TEXT_FOUND_MESSAGE, thrown.getMessage());
        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository, atLeastOnce()).save(captor.capture());
        Assertions.assertEquals(DocumentStatus.FAILED, captor.getValue().getStatus());
        verify(vectorStore, never()).add(any());
    }

    @Test
    public void existingVectorsAreClearedBeforeIngesting() throws IOException {
        MockMultipartFile image = createImage("scan.png");
        Document oldDocument = new Document("Old document text");
        stubCloudinary(image);
        when(ocrService.extractText(image)).thenReturn(FAKE_OCR_TEXT);
        stubSaveReturnsSameRecord();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(oldDocument));

        multiImageIngestionService.ingestImages(new MultipartFile[]{image});

        verify(vectorStore).delete(List.of(oldDocument.getId()));
    }

    @Test
    public void imageWithBlankFilenameUsesDefaultFilename() throws IOException {
        // a blank original filename is replaced with the default one (upload.pdf)
        MockMultipartFile image = createImage("   ");
        stubCloudinary(image);
        when(ocrService.extractText(image)).thenReturn(FAKE_OCR_TEXT);
        stubSaveReturnsSameRecord();

        UploadResponse response = multiImageIngestionService.ingestImages(new MultipartFile[]{image});

        Assertions.assertEquals(DEFAULT_FILENAME, response.getFile());
    }

    // creates a small fake image file with some content so it is not empty
    private MockMultipartFile createImage(String filename) {
        return new MockMultipartFile("files", filename, "image/png", "image bytes".getBytes());
    }

    // makes the mocked Cloudinary service return the fake public id and url for this image
    private void stubCloudinary(MockMultipartFile image) throws IOException {
        when(cloudinaryService.uploadImage(image)).thenReturn(FAKE_PUBLIC_ID);
        when(cloudinaryService.getImageUrl(FAKE_PUBLIC_ID)).thenReturn(FAKE_CLOUDINARY_URL);
    }

    // the service uses the record returned by save(), so the mock must hand back the same record it was given
    private void stubSaveReturnsSameRecord() {
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}