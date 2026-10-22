package com.example.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant that answers questions using ONLY the provided context.
            If the answer is not in the context, reply: "I don't know based on the provided documents."
            Keep answers concise and accurate.
            """;

    private static final String RAG_PROMPT = """
            {query}

            Context information is below, surrounded by ---------------------

            ---------------------
            {question_answer_context}
            ---------------------

            Answer the question using only the context above. Do not use prior knowledge.
            If the context does not contain the answer, say you don't know.
            """;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder,
                          VectorStore vectorStore,
                          @Value("${app.rag.top-k:5}") int topK,
                          @Value("${app.rag.similarity-threshold:0.5}") double threshold) {

        var advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .build())
                .promptTemplate(new PromptTemplate(RAG_PROMPT))
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(advisor)
                .build();
    }
}
