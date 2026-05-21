package com.pdfchat.service;

import com.pdfchat.model.dto.AskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public AskResponse ask(String question) {
        // Similarity search in Pinecone — mirrors index.query(vector, top_k=5)
        // Spring AI embeds the question internally before searching
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .build()
        );

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant documents found for question: {}", question);
        }

        // Build context string
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // Collect source filenames
        List<String> sources = relevantDocs.stream()
                .map(doc -> (String) doc.getMetadata().getOrDefault("source", "unknown"))
                .distinct()
                .collect(Collectors.toList());

        // Call OpenAI ChatModel with RAG prompt
        String answer = chatClient.prompt(
                new Prompt(List.of(
                        new SystemMessage(
                                "You are a helpful PDF assistant. " +
                                "Answer questions using ONLY the provided context. " +
                                "If the answer is not in the context, say so clearly."
                        ),
                        new UserMessage(
                                "Context:\n" + context +
                                "\n\nQuestion:\n" + question +
                                "\n\nAnswer:"
                        )
                ))
        ).call().content();

        //OpenAI response
        return AskResponse.builder()
                .output_text(answer)
                .sources(sources)
                .build();
    }
}
