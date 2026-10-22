package com.example.rag.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** Reads the file, splits it into chunks, embeds and stores them. Returns the generated document id. */
    public IngestResult ingest(MultipartFile file) throws IOException {
        String documentId = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();

        var resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        List<Document> raw = new TikaDocumentReader(resource).get();
        List<Document> chunks = splitter.apply(raw);

        // Tag every chunk so we can filter by document and delete it later.
        chunks.forEach(c -> {
            c.getMetadata().put("documentId", documentId);
            c.getMetadata().put("filename", filename);
        });

        vectorStore.add(chunks);
        log.info("Ingested '{}' as {} ({} chunks)", filename, documentId, chunks.size());
        return new IngestResult(documentId, filename, chunks.size());
    }

    public void delete(String documentId) {
        vectorStore.delete("documentId == '" + documentId.replace("'", "") + "'");
    }

    public record IngestResult(String documentId, String filename, int chunks) {
    }
}
