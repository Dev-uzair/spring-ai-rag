# Spring AI RAG + pgvector + Docker Compose

Upload documents (PDF, DOCX, TXT, HTML), then ask questions answered only from those documents.

**Stack:** Spring Boot 3.5, Spring AI (`QuestionAnswerAdvisor`), PostgreSQL + pgvector, Apache Tika, Docker Compose.

## Run it

```bash
cp .env.example .env          # put your OPENAI_API_KEY in .env
docker compose up --build
```

Open http://localhost:8080 for the simple UI, or use the API below.

### Fully local (no API key) with Ollama

In `.env`, comment out Option A and uncomment Option B, then:

```bash
docker compose --profile ollama up --build
```

The first start downloads `llama3.2` and `nomic-embed-text` (a few GB). The app may fail on its first
request until the pull finishes; check with `docker logs -f rag-ollama-pull`.

> If you switch embedding models, change `EMBEDDING_DIMENSIONS` too (1536 for text-embedding-3-small,
> 768 for nomic-embed-text) and reset the vector table: `docker compose down -v`.

## API

```bash
# 1. Upload a document -> returns documentId
curl -F "file=@/path/to/policy.pdf" http://localhost:8080/api/documents

# 2. Ask across ALL documents
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"What is the cancellation policy?"}'

# 3. Ask about ONE document only
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"What is the notice period?","documentId":"<id-from-upload>"}'

# 4. Stream the answer (SSE)
curl -N -X POST http://localhost:8080/api/ask/stream \
  -H "Content-Type: application/json" \
  -d '{"question":"Summarize the document"}'

# 5. Delete a document's chunks
curl -X DELETE http://localhost:8080/api/documents/<id>
```

## Project layout

```
src/main/java/com/example/rag/
  RagApplication.java
  config/ChatConfig.java        ChatClient + QuestionAnswerAdvisor (topK, threshold, strict prompt)
  service/IngestionService.java Tika read -> TokenTextSplitter -> embed -> pgvector
  web/RagController.java        upload / delete / ask / ask-stream
src/main/resources/
  application.yml               all settings are env-driven
  static/index.html             minimal upload + chat page
Dockerfile                      multi-stage build (Maven -> JRE 21)
docker-compose.yml              pgvector + app (+ optional Ollama profile)
```

## Tuning

| Setting | Env var | Effect |
|---|---|---|
| Chunks sent to the LLM | `RAG_TOP_K` | More context vs. more noise and cost |
| Minimum similarity | `RAG_SIMILARITY_THRESHOLD` | Raise to cut irrelevant chunks; lower if answers say "I don't know" too often |
| Chat / embedding model | `CHAT_MODEL`, `EMBEDDING_MODEL` | Any OpenAI-compatible model |

## Notes

- Not yet compiled by the author: the build environment had no access to Maven Central. If `mvn package`
  reports a missing artifact or API, bump `spring-ai.version` in `pom.xml` to the latest 1.1.x release
  (check https://docs.spring.io/spring-ai/reference/) and adjust any renamed API.
- No authentication is included. Add Spring Security before exposing this beyond localhost.
- Uploaded files are not stored on disk, only their embedded chunks are kept in Postgres.
