package com.pdfchat.service;

import com.pdfchat.constants.PromptConstant;
import com.pdfchat.service.impl.OcrServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrServiceTests {

    private static final String FILE_PARAM_NAME = "file";
    private static final String FILE_NAME = "scan.png";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String DEFAULT_CONTENT_TYPE = "image/jpeg";
    private static final String SAMPLE_IMAGE_TEXT = "fake-image-bytes";
    private static final String EXTRACTED_TEXT = "Hello from the scanned page";
    private static final int MAX_OCR_CHARS = 6000;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @InjectMocks
    private OcrServiceImpl ocrService;

    private void givenModelReturns(String modelOutput) {
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(modelOutput);
    }

    /**
     * Builds a valid multipart image file with the given content type.
     *
     * @param contentType the MIME type to set on the file, may be null
     * @return a non-empty mock multipart file
     */
    private MultipartFile buildImageFile(String contentType) {
        return new MockMultipartFile(
                FILE_PARAM_NAME,
                FILE_NAME,
                contentType,
                SAMPLE_IMAGE_TEXT.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Runs extractText and returns the Prompt that was sent to the ChatClient.
     *
     * @param file the file to extract text from
     * @return the captured Prompt
     * @throws IOException if the file cannot be read
     */
    private Prompt extractAndCapturePrompt(MultipartFile file) throws IOException {
        ocrService.extractText(file);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        return promptCaptor.getValue();
    }

    @Test
    @DisplayName("throws IllegalArgumentException when file is null")
    void shouldThrowIllegalArgumentExceptionWhenFileIsNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ocrService.extractText(null))
                .withMessage("Uploaded file is missing or empty");

        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("throws IllegalArgumentException when file is empty")
    void shouldThrowIllegalArgumentExceptionWhenFileIsEmpty() {
        MultipartFile emptyFile = new MockMultipartFile(
                FILE_PARAM_NAME,
                FILE_NAME,
                PNG_CONTENT_TYPE,
                new byte[0]
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ocrService.extractText(emptyFile)
        );

        assertEquals("Uploaded file is missing or empty", exception.getMessage());
        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("returns the extracted text for a valid image")
    void shouldReturnModelTextWhenImageIsValid() throws IOException {
        givenModelReturns(EXTRACTED_TEXT);

        String result = ocrService.extractText(buildImageFile(PNG_CONTENT_TYPE));

        assertEquals(EXTRACTED_TEXT, result);
    }

    @Test
    @DisplayName("sends system message then user message with the OCR prompts")
    void shouldSendSystemMessageThenUserMessageWhenImageIsValid() throws IOException {
        givenModelReturns(EXTRACTED_TEXT);

        Prompt sentPrompt = extractAndCapturePrompt(buildImageFile(PNG_CONTENT_TYPE));

        List<Message> messages = sentPrompt.getInstructions();
        assertEquals(2, messages.size());

        // first message is the system prompt
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, messages.get(0));
        assertEquals(PromptConstant.OCR_SYSTEM_PROMPT, systemMessage.getText());

        // second message is the user prompt carrying the image
        UserMessage userMessage = assertInstanceOf(UserMessage.class, messages.get(1));
        assertEquals(PromptConstant.OCR_USER_PROMPT, userMessage.getText());
    }

    @Test
    @DisplayName("attaches the image as media using the file's content type")
    void shouldAttachImageWithFileContentTypeWhenImageIsValid() throws IOException {
        givenModelReturns(EXTRACTED_TEXT);

        Prompt sentPrompt = extractAndCapturePrompt(buildImageFile(PNG_CONTENT_TYPE));

        UserMessage userMessage = assertInstanceOf(UserMessage.class, sentPrompt.getInstructions().get(1));
        assertEquals(1, userMessage.getMedia().size());

        Media attachedMedia = userMessage.getMedia().get(0);
        assertEquals(PNG_CONTENT_TYPE, attachedMedia.getMimeType().toString());
    }

    @Test
    @DisplayName("defaults to image/jpeg when content type is null")
    void shouldDefaultToJpegWhenContentTypeIsNull() throws IOException {
        givenModelReturns(EXTRACTED_TEXT);

        Prompt sentPrompt = extractAndCapturePrompt(buildImageFile(null));

        UserMessage userMessage = assertInstanceOf(UserMessage.class, sentPrompt.getInstructions().get(1));
        Media attachedMedia = userMessage.getMedia().get(0);
        assertEquals(DEFAULT_CONTENT_TYPE, attachedMedia.getMimeType().toString());
    }

    /**
     * A null model response should become an empty string, not a NullPointerException.
     */
    @Test
    @DisplayName("returns empty string when the model returns null")
    void shouldReturnEmptyStringWhenModelReturnsNull() throws IOException {
        givenModelReturns(null);

        String result = ocrService.extractText(buildImageFile(PNG_CONTENT_TYPE));

        assertEquals("", result);
    }

    @Test
    @DisplayName("returns empty string when the model returns blank text")
    void shouldReturnEmptyStringWhenModelReturnsBlank() throws IOException {
        givenModelReturns("   \n\t  ");

        String result = ocrService.extractText(buildImageFile(PNG_CONTENT_TYPE));

        assertEquals("", result);
    }

    @Test
    @DisplayName("trims text longer than 6000 characters to 6000")
    void shouldTrimTextWhenOverMaxChars() throws IOException {
        String oversizedText = "a".repeat(MAX_OCR_CHARS + 500);
        givenModelReturns(oversizedText);

        String result = ocrService.extractText(buildImageFile(PNG_CONTENT_TYPE));

        assertEquals(MAX_OCR_CHARS, result.length());
        assertEquals(oversizedText.substring(0, MAX_OCR_CHARS), result);
    }

    @Test
    @DisplayName("does not trim text that is exactly 6000 characters")
    void shouldKeepTextWhenExactlyAtMaxChars() throws IOException {
        String boundaryText = "b".repeat(MAX_OCR_CHARS);
        givenModelReturns(boundaryText);

        String result = ocrService.extractText(buildImageFile(PNG_CONTENT_TYPE));

        assertEquals(boundaryText, result);
    }

    @Test
    @DisplayName("does not trim text shorter than 6000 characters")
    void shouldKeepTextWhenUnderMaxChars() throws IOException {
        String shortText = "c".repeat(MAX_OCR_CHARS - 1);
        givenModelReturns(shortText);

        String result = ocrService.extractText(buildImageFile(PNG_CONTENT_TYPE));

        assertEquals(shortText, result);
    }

    @Test
    @DisplayName("propagates IOException when file bytes cannot be read")
    void shouldPropagateIOExceptionWhenFileBytesCannotBeRead() throws IOException {
        MultipartFile brokenFile = mock(MultipartFile.class);
        when(brokenFile.isEmpty()).thenReturn(false);
        when(brokenFile.getContentType()).thenReturn(PNG_CONTENT_TYPE);
        when(brokenFile.getBytes()).thenThrow(new IOException("disk read failed"));

        IOException exception = assertThrows(
                IOException.class,
                () -> ocrService.extractText(brokenFile)
        );

        assertEquals("disk read failed", exception.getMessage());
        verify(chatClient, never()).prompt(any(Prompt.class));
    }

    @Test
    @DisplayName("propagates runtime exceptions thrown by the model call")
    void shouldPropagateExceptionWhenModelCallFails() {
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new IllegalStateException("model unavailable"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ocrService.extractText(buildImageFile(PNG_CONTENT_TYPE))
        );

        assertEquals("model unavailable", exception.getMessage());
    }
}