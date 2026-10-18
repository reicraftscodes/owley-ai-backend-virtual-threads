package com.pdfchat.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.pdfchat.service.impl.CloudinaryServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static com.pdfchat.constants.CloudinaryConstant.CLOUDINARY_URL_TEMPLATE;
import static com.pdfchat.constants.CloudinaryConstant.INVALID_IMAGE;
import static com.pdfchat.constants.CloudinaryConstant.NO_FILE_PROVIDED;
import static com.pdfchat.constants.CloudinaryConstant.ONLY_FILES_ALLOWED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CloudinaryServiceTests {

    // fake settings, in the real app these come from application.properties with @Value
    private static final String FAKE_CLOUD_NAME = "demo-cloud";
    private static final String FAKE_PDF_FOLDER = "pdfs";
    private static final String FAKE_IMAGE_FOLDER = "images";

    // a public id is the file name without its extension, an underscore and a random UUID
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    // deep stubs let the mock answer the chained call cloudinary.uploader().upload(...)
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Cloudinary cloudinary;

    @InjectMocks
    private CloudinaryServiceImpl cloudinaryService;

    @BeforeEach
    public void setUp() {
        // @Value fields are only filled in by Spring, so they are set by hand in a unit test
        ReflectionTestUtils.setField(cloudinaryService, "pdfFolder", FAKE_PDF_FOLDER);
        ReflectionTestUtils.setField(cloudinaryService, "imageFolder", FAKE_IMAGE_FOLDER);
        ReflectionTestUtils.setField(cloudinaryService, "cloudName", FAKE_CLOUD_NAME);
    }


    @Test
    public void uploadPdfReturnsPublicIdWithUuid() throws IOException {
        MockMultipartFile pdf = createFile("report.pdf", "application/pdf");

        String publicId = cloudinaryService.uploadPdf(pdf);

        Assertions.assertTrue(publicId.matches("report_" + UUID_PATTERN));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void uploadPdfSendsFileAndSettingsToCloudinary() throws IOException {
        MockMultipartFile pdf = createFile("report.pdf", "application/pdf");

        String publicId = cloudinaryService.uploadPdf(pdf);

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(cloudinary.uploader()).upload(eq(pdf.getBytes()), paramsCaptor.capture());

        Map<String, Object> params = paramsCaptor.getValue();

        Assertions.assertEquals(FAKE_PDF_FOLDER, params.get("folder"));
        Assertions.assertEquals(publicId, params.get("public_id"));
        Assertions.assertEquals("raw", params.get("resource_type"));
        Assertions.assertEquals("authenticated", params.get("type"));
        Assertions.assertEquals(false, params.get("overwrite"));
    }

    @Test
    public void uploadPdfWithUppercaseExtension() throws IOException {
        MockMultipartFile pdf = createFile("REPORT.PDF", "application/pdf");

        String publicId = cloudinaryService.uploadPdf(pdf);

        Assertions.assertTrue(publicId.matches("REPORT_" + UUID_PATTERN));
    }

    @Test
    public void uploadPdfWithNullFile() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> cloudinaryService.uploadPdf(null));

        Assertions.assertEquals(NO_FILE_PROVIDED, thrown.getMessage());
    }

    @Test
    public void uploadPdfWithEmptyFile() {
        MockMultipartFile emptyPdf = new MockMultipartFile("pdf", "report.pdf", "application/pdf", new byte[0]);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> cloudinaryService.uploadPdf(emptyPdf));

        Assertions.assertEquals(NO_FILE_PROVIDED, thrown.getMessage());
    }

    @Test
    public void uploadPdfWithNonPdfFile() {
        MockMultipartFile textFile = createFile("notes.txt", "text/plain");

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> cloudinaryService.uploadPdf(textFile));

        Assertions.assertEquals("Only pdf files allowed", thrown.getMessage());
    }

    @Test
    public void uploadPdfWithNoFilename() {
        // a plain mock gives a real null name (MockMultipartFile would turn it into an empty string)
        MultipartFile fileWithoutName = mock(MultipartFile.class);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> cloudinaryService.uploadPdf(fileWithoutName));

        Assertions.assertEquals("Only pdf files allowed", thrown.getMessage());
    }

    @Test
    public void uploadPdfWhenCloudinaryFails() throws IOException {
        MockMultipartFile pdf = createFile("report.pdf", "application/pdf");
        Uploader uploader = cloudinary.uploader();
        when(uploader.upload(any(), any(Map.class))).thenThrow(new IOException("Cloudinary is down"));

        IOException thrown = Assertions.assertThrows(IOException.class,
                () -> cloudinaryService.uploadPdf(pdf));

        Assertions.assertEquals("Cloudinary is down", thrown.getMessage());
    }

    @Test
    public void getPdfUrlBuildsRawUploadUrl() {
        String url = cloudinaryService.getPdfUrl("report_123");

        Assertions.assertEquals(CLOUDINARY_URL_TEMPLATE + FAKE_CLOUD_NAME + "/raw/upload/pdfs/report_123", url);
    }


    @Test
    public void uploadImageReturnsPublicIdWithUuid() throws IOException {
        MockMultipartFile image = createFile("scan.png", "image/png");

        String publicId = cloudinaryService.uploadImage(image);

        Assertions.assertTrue(publicId.matches("scan_" + UUID_PATTERN));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void uploadImageSendsFileAndSettingsToCloudinary() throws IOException {
        MockMultipartFile image = createFile("scan.png", "image/png");

        String publicId = cloudinaryService.uploadImage(image);

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cloudinary.uploader()).upload(eq(image.getBytes()), paramsCaptor.capture());

        Map<String, Object> params = paramsCaptor.getValue();

        Assertions.assertEquals(FAKE_IMAGE_FOLDER, params.get("folder"));
        Assertions.assertEquals(publicId, params.get("public_id"));
        Assertions.assertEquals("image", params.get("resource_type"));
        Assertions.assertEquals("authenticated", params.get("type"));
        Assertions.assertEquals(false, params.get("overwrite"));
    }

    // runs once for each allowed extension, in lower and upper case
    @ParameterizedTest
    @ValueSource(strings = {"photo.png", "photo.jpg", "photo.jpeg", "photo.webp", "PHOTO.PNG"})
    public void uploadImageWithAllowedFilenames(String filename) {
        MockMultipartFile image = createFile(filename, "image/png");

        Assertions.assertDoesNotThrow(() -> cloudinaryService.uploadImage(image));
    }

    @Test
    public void uploadImageWithEmptyFile() {
        MockMultipartFile emptyImage = new MockMultipartFile("files", "scan.png", "image/png", new byte[0]);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> cloudinaryService.uploadImage(emptyImage));

        Assertions.assertEquals(INVALID_IMAGE, thrown.getMessage());
    }

    @Test
    public void uploadImageWithNoFilename() {
        MultipartFile fileWithoutName = mock(MultipartFile.class);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> cloudinaryService.uploadImage(fileWithoutName));

        Assertions.assertEquals(INVALID_IMAGE, thrown.getMessage());
    }

    @Test
    public void uploadImageWithGif() {
        MockMultipartFile gif = createFile("animation.gif", "image/gif");

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> cloudinaryService.uploadImage(gif));

        Assertions.assertEquals(ONLY_FILES_ALLOWED, thrown.getMessage());
    }

    @Test
    public void getImageUrlBuildsImageUploadUrl() {
        String url = cloudinaryService.getImageUrl("scan_123");

        Assertions.assertEquals(CLOUDINARY_URL_TEMPLATE + FAKE_CLOUD_NAME + "/image/upload/images/scan_123", url);
    }

    // creates a small fake file with some content so it is not empty
    private MockMultipartFile createFile(String filename, String contentType) {
        return new MockMultipartFile("file", filename, contentType, "file bytes".getBytes());
    }
}