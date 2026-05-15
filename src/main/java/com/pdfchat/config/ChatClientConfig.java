package com.pdfchat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures the Spring AI ChatClient bean.
 * Spring AI auto-wires OpenAiChatModel from application.properties,
 * this just wraps it in the fluent ChatClient API.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
