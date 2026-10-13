package com.pdfchat.service;

import com.pdfchat.constants.PromptConstant;
import com.pdfchat.model.AskResponse;
import com.pdfchat.service.impl.RagServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static com.pdfchat.constants.RagConstant.CONTEXT_SEPARATOR;
import static com.pdfchat.constants.RagConstant.RESULT_SUCCESS;
import static com.pdfchat.constants.RagConstant.TOP_K;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTests {

    private static final String QUESTION = "What is the password policy?";
    private static final String FAKE_ANSWER = "Passwords must be at least 12 characters.";
    private static final String NO_ANSWER_TEXT = "The answer is not available in the provided document.";
    private static final String FAKE_URL = "https://cloudinary.test/policy.pdf";
    private static final String BLANK_URL = "";
    private static final String PASSWORD_CHUNK = "Passwords need 12 characters.";
    private static final String FIRST_CHUNK = "First chunk.";
    private static final String SECOND_CHUNK = "Second chunk.";
    private static final String SOURCE_FILE = "policy.pdf";
    private static final String FIRST_SOURCE_FILE = "one.pdf";
    private static final String SECOND_SOURCE_FILE = "two.pdf";
    private static final String DOC_TYPE_PDF = "PDF";
    private static final String METADATA_SOURCE = "source";
    private static final String METADATA_TYPE = "type";
    private static final String METADATA_CLOUDINARY_URL = "cloudinary_url";
    private static final String URL_LABEL = "URL:";
    private static final String CONTEXT_PREFIX = "Context:\n\n";
    private static final String HEADER_WITH_URL = "[SOURCE: policy.pdf | TYPE: PDF | URL: " + FAKE_URL + "]\n";
    private static final String HEADER_WITHOUT_URL = "[SOURCE: policy.pdf | TYPE: PDF]\n";
    private static final String HEADER_WITH_DEFAULTS = "[SOURCE: unknown | TYPE: UNKNOWN]\n";
    private static final String FIRST_HEADER = "[SOURCE: one.pdf | TYPE: PDF]\n";
    private static final String SECOND_HEADER = "[SOURCE: two.pdf | TYPE: PDF]\n";
    private static final int SYSTEM_MESSAGE_INDEX = 0;
    private static final int USER_MESSAGE_INDEX = 1;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @InjectMocks
    private RagServiceImpl ragService;

    /**
     * Stubs the vector store so the similarity search finds the given documents.
     *
     * @param documents the documents the search should return, may be empty
     */
    private void givenDocumentsFound(List<Document> documents) {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);
    }

    /**
     * Stubs the ChatClient fluent chain (prompt -> call -> content) so the
     * mocked model returns the given answer.
     *
     * @param modelContent the text the mocked model should return from content()
     */
    private void givenModelReturns(String modelContent) {
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(modelContent);
    }

    /**
     * Captures the Prompt that RagServiceImpl sent to the chat model.
     *
     * @return the Prompt passed to chatClient.prompt(...)
     */
    private Prompt capturePromptSentToModel() {
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        return promptCaptor.getValue();
    }

    /**
     * Gets the user message from the prompt, the second message holds the context and question.
     *
     * @param prompt the prompt sent to the chat model
     * @return the text of the user message
     */
    private String getUserMessage(Prompt prompt) {
        return prompt.getInstructions().get(USER_MESSAGE_INDEX).getText();
    }

    @Test
    @DisplayName("returns the answer when the chat model responds")
    void shouldReturnAnswerWhenChatModelResponds() {
        Document document = new Document(PASSWORD_CHUNK, Map.of(METADATA_SOURCE, SOURCE_FILE));
        givenDocumentsFound(List.of(document));
        givenModelReturns(FAKE_ANSWER);

        AskResponse response = ragService.ask(QUESTION);

        assertEquals(FAKE_ANSWER, response.getOutputText());
        assertTrue(response.getResult());
        assertEquals(RESULT_SUCCESS, response.getResultMessage());
    }

    @Test
    @DisplayName("searches the vector store with the question and top K")
    void shouldSearchVectorStoreWithQuestionAndTopKWhenAsked() {
        givenDocumentsFound(List.of());
        givenModelReturns(FAKE_ANSWER);

        ragService.ask(QUESTION);

        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());
        assertEquals(QUESTION, searchCaptor.getValue().getQuery());
        assertEquals(TOP_K, searchCaptor.getValue().getTopK());
    }

    @Test
    @DisplayName("sends the system prompt first then the user prompt")
    void shouldSendSystemPromptFirstThenUserPromptWhenAsked() {
        givenDocumentsFound(List.of());
        givenModelReturns(FAKE_ANSWER);

        ragService.ask(QUESTION);

        Prompt prompt = capturePromptSentToModel();
        assertEquals(2, prompt.getInstructions().size());
        assertEquals(PromptConstant.SYSTEM_PROMPT, prompt.getInstructions().get(SYSTEM_MESSAGE_INDEX).getText());
        assertTrue(getUserMessage(prompt).contains(QUESTION));
    }

    @Test
    @DisplayName("includes the source, type and URL in the context when metadata is present")
    void shouldIncludeSourceTypeAndUrlInContextWhenMetadataIsPresent() {
        Document document = new Document(PASSWORD_CHUNK, Map.of(
                METADATA_SOURCE, SOURCE_FILE,
                METADATA_TYPE, DOC_TYPE_PDF,
                METADATA_CLOUDINARY_URL, FAKE_URL));
        givenDocumentsFound(List.of(document));
        givenModelReturns(FAKE_ANSWER);

        ragService.ask(QUESTION);

        String userMessage = getUserMessage(capturePromptSentToModel());
        assertTrue(userMessage.contains(HEADER_WITH_URL + PASSWORD_CHUNK));
    }

    @Test
    @DisplayName("leaves the URL out of the context when the URL is blank")
    void shouldLeaveOutUrlFromContextWhenUrlIsBlank() {
        Document document = new Document(PASSWORD_CHUNK, Map.of(
                METADATA_SOURCE, SOURCE_FILE,
                METADATA_TYPE, DOC_TYPE_PDF,
                METADATA_CLOUDINARY_URL, BLANK_URL));
        givenDocumentsFound(List.of(document));
        givenModelReturns(FAKE_ANSWER);

        ragService.ask(QUESTION);

        String userMessage = getUserMessage(capturePromptSentToModel());
        assertTrue(userMessage.contains(HEADER_WITHOUT_URL + PASSWORD_CHUNK));
        assertFalse(userMessage.contains(URL_LABEL));
    }

    @Test
    @DisplayName("uses default values in the context when metadata is missing")
    void shouldUseDefaultsInContextWhenMetadataIsMissing() {
        Document document = new Document(PASSWORD_CHUNK);
        givenDocumentsFound(List.of(document));
        givenModelReturns(FAKE_ANSWER);

        ragService.ask(QUESTION);

        String userMessage = getUserMessage(capturePromptSentToModel());
        assertTrue(userMessage.contains(HEADER_WITH_DEFAULTS + PASSWORD_CHUNK));
    }

    @Test
    @DisplayName("joins all documents with the separator when several are found")
    void shouldJoinAllDocumentsWithSeparatorWhenMultipleDocumentsFound() {
        Document first = new Document(FIRST_CHUNK, Map.of(METADATA_SOURCE, FIRST_SOURCE_FILE, METADATA_TYPE, DOC_TYPE_PDF));
        Document second = new Document(SECOND_CHUNK, Map.of(METADATA_SOURCE, SECOND_SOURCE_FILE, METADATA_TYPE, DOC_TYPE_PDF));
        givenDocumentsFound(List.of(first, second));
        givenModelReturns(FAKE_ANSWER);

        ragService.ask(QUESTION);

        String userMessage = getUserMessage(capturePromptSentToModel());
        String expectedContext = FIRST_HEADER + FIRST_CHUNK + CONTEXT_SEPARATOR + SECOND_HEADER + SECOND_CHUNK;
        assertTrue(userMessage.contains(expectedContext));
    }

    @Test
    @DisplayName("still calls the chat model when no documents are found")
    void shouldStillCallChatModelWhenNoDocumentsFound() {
        givenDocumentsFound(List.of());
        givenModelReturns(NO_ANSWER_TEXT);

        AskResponse response = ragService.ask(QUESTION);

        // the context is empty but the model is still asked, so it can reply that there is no answer
        assertEquals(NO_ANSWER_TEXT, response.getOutputText());
        assertTrue(response.getResult());
        assertTrue(getUserMessage(capturePromptSentToModel()).startsWith(CONTEXT_PREFIX));
    }
}