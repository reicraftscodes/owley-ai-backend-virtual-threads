package com.pdfchat.service;

import com.pdfchat.service.impl.ImageValidationServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.Mockito.mock;

public class ImageValidationServiceTests {

    private final ImageValidationServiceImpl imageValidationService = new ImageValidationServiceImpl();

    @Test
    public void validateNullFile() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> imageValidationService.validate(null));

        Assertions.assertEquals("Invalid image", thrown.getMessage());
    }

    @Test
    public void validateEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("files", "photo.png", "image/png", new byte[0]);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> imageValidationService.validate(emptyFile));

        Assertions.assertEquals("Invalid image", thrown.getMessage());
    }

    @Test
    public void validateFileWithNoFilename() {
        // a plain mock gives a real null name (MockMultipartFile would turn it into an empty string)
        MultipartFile fileWithoutName = mock(MultipartFile.class);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> imageValidationService.validate(fileWithoutName));

        Assertions.assertEquals("Invalid image", thrown.getMessage());
    }

    @Test
    public void validateTextFileIsRejected() {
        MockMultipartFile textFile = createImage("notes.txt", "text/plain");

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> imageValidationService.validate(textFile));

        Assertions.assertEquals("Only PNG, JPG, JPEG, WEBP images allowed", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.png", "photo.jpg", "photo.jpeg", "photo.webp", "PHOTO.PNG"})
    public void validateAllowedFilenames(String filename) {
        MockMultipartFile image = createImage(filename, null);

        Assertions.assertDoesNotThrow(() -> imageValidationService.validate(image));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/jpg", "image/jpeg", "image/webp"})
    public void validateAllowedContentTypes(String contentType) {
        MockMultipartFile image = createImage("photo", contentType);

        Assertions.assertDoesNotThrow(() -> imageValidationService.validate(image));
    }


    @Test
    public void validateBatchWithNoImages() {
        IllegalArgumentException thrownForNull = Assertions.assertThrows(IllegalArgumentException.class,
                () -> imageValidationService.validateBatch(null));
        IllegalArgumentException thrownForEmpty = Assertions.assertThrows(IllegalArgumentException.class,
                () -> imageValidationService.validateBatch(new MultipartFile[0]));

        Assertions.assertEquals("No images provided", thrownForNull.getMessage());
        Assertions.assertEquals("No images provided", thrownForEmpty.getMessage());
    }

    @Test
    public void validateBatchWithMoreThanFiveImages() {
        MultipartFile[] sixImages = createImages(6);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> imageValidationService.validateBatch(sixImages));

        Assertions.assertEquals("Max 5 images allowed per request", thrown.getMessage());
    }

    @Test
    public void validateBatchWithFiveImagesIsAllowed() {
        // five is the limit, so this is still fine
        MultipartFile[] fiveImages = createImages(5);

        Assertions.assertDoesNotThrow(() -> imageValidationService.validateBatch(fiveImages));
    }

    @Test
    public void validateBatchWithOneBadImage() {
        MultipartFile[] images = {
                createImage("one.png", "image/png"),
                createImage("notes.txt", "text/plain")
        };

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> imageValidationService.validateBatch(images));

        Assertions.assertEquals("Only PNG, JPG, JPEG, WEBP images allowed", thrown.getMessage());
    }

    // creates a small fake image file with some content so it is not empty
    private MockMultipartFile createImage(String filename, String contentType) {
        return new MockMultipartFile("files", filename, contentType, "image bytes".getBytes());
    }

    // creates the given number of valid fake PNG files
    private MultipartFile[] createImages(int count) {
        MultipartFile[] images = new MultipartFile[count];
        for (int i = 0; i < count; i++) {
            images[i] = createImage("image" + i + ".png", "image/png");
        }
        return images;
    }
}