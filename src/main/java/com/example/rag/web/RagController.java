package com.example.rag.web;

import java.io.IOException;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.rag.service.IngestionService;
import com.example.rag.service.IngestionService.IngestResult;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class RagController {

    private final ChatClient chatClient;
    private final IngestionService ingestionService;

    public RagController(ChatClient chatClient, IngestionService ingestionService) {
        this.chatClient = chatClient;
        this.ingestionService = ingestionService;
    }

    public record AskRequest(String question, String documentId) {
    }

    public record AskResponse(String answer) {
    }

    /** Upload a PDF / DOCX / TXT / HTML file. It is chunked, embedded and stored in pgvector. */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestResult upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ingestionService.ingest(file);
    }

    /** Remove all chunks of one uploaded document. */
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        ingestionService.delete(documentId);
        return ResponseEntity.noContent().build();
    }

    /** Ask a question. Optionally restrict retrieval to a single document with documentId. */
    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest req) {
        var spec = chatClient.prompt().user(req.question());
        if (req.documentId() != null && !req.documentId().isBlank()) {
            String filter = "documentId == '" + req.documentId().replace("'", "") + "'";
            spec = spec.advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filter));
        }
        return new AskResponse(spec.call().content());
    }

    /** Same as /ask but streams tokens (Server-Sent Events) for a chat-style UI. */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@RequestBody AskRequest req) {
        var spec = chatClient.prompt().user(req.question());
        if (req.documentId() != null && !req.documentId().isBlank()) {
            String filter = "documentId == '" + req.documentId().replace("'", "") + "'";
            spec = spec.advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filter));
        }
        return spec.stream().content();
    }

    @org.springframework.web.bind.annotation.GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
