package com.pdfchat.service.impl;

import com.pdfchat.constants.PromptConstant;
import com.pdfchat.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class OcrServiceImpl implements OcrService {

    @Autowired
    private ChatClient chatClient;

    @Override
    public String extractText(MultipartFile file) throws IOException {

        // validate file
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is missing or empty");
        }

        // determine MIME type
        String contentType = file.getContentType() != null
                ? file.getContentType()
                : "image/jpeg";

        // create Media object from uploaded file
        Media media = new Media(
                MimeTypeUtils.parseMimeType(contentType),
                new ByteArrayResource(file.getBytes())
        );

        // attach media to user message
        UserMessage userMessage = UserMessage.builder()
                .text(PromptConstant.OCR_USER_PROMPT)
                .media(media)
                .build();

        // build prompt
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PromptConstant.OCR_SYSTEM_PROMPT),
                userMessage
        ));

        // call model
        String result = chatClient.prompt(prompt)
                .call()
                .content();

        log.info("OCR extracted {} characters", result != null ? result.length() : 0);

        // trim OCR text to avoid downstream token overflow
        return trimText(result, 6000);
    }

    private String trimText(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return text.length() > maxChars
                ? text.substring(0, maxChars)
                : text;
    }
}
