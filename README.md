# SupportPilot

An AI customer-support agent built with **Spring Boot 3**, **Spring AI** and **Angular**. It answers policy questions with **RAG** over a knowledge base stored in **pgvector**, looks up orders through **tool calling**, and handles refunds with a **human in the loop**: the agent can only create a *pending* refund request, and a person approves or rejects it in an approver console.

The model provider is chosen by configuration only. **Ollama** is the default (local, free, no API key); **OpenAI** and **Gemini** can be switched on with environment variables.

> The store ("ByteBay Electronics"), customers, orders and policies are fictional sample data that load automatically at startup.

## Features

- **RAG over a knowledge base**: Markdown policy articles (shipping, returns and refunds, warranty, account and payments) are split per section, embedded and stored in pgvector at startup. Spring AI's `QuestionAnswerAdvisor` retrieves relevant sections for each question, and the UI shows which articles an answer used.
- **Tool calling**: `trackOrder`, `listMyOrders`, `requestRefund` and `myRefundRequests` are `@Tool` methods the model can call.
- **Human-approved refunds**: `requestRefund` only creates a `PENDING` request after checking the refund policy (delivered orders only, 30-day window, amount within the refundable balance, one pending request per order). Money "moves" (the order's refunded amount and status change) only when an approver accepts it.
- **Safe customer identity for tools**: the signed-in customer is passed to tools through Spring AI's `ToolContext`, which the model cannot see or change. A prompt like "show me bob@example.com's orders" cannot reach another customer's data, and orders of other customers are reported as not found.
- **Conversation memory** per customer and conversation (`MessageChatMemoryAdvisor`).
- **Provider switching by configuration**: all three provider starters are on the classpath, and `spring.ai.model.chat` / `spring.ai.model.embedding` pick the active one. The app starts without any API key.
- **Resilient startup**: if the embedding provider is not reachable, the app still starts. The knowledge-base status is shown in the UI and loading can be retried.
- **Sample data at startup**: 8 orders for 3 demo customers, with dates relative to "now" so refund-window scenarios always behave the same.
- **Angular UI** with Tailwind CSS: a customer chat with order and refund sidebars, action cards for tool results, knowledge-base sources, and an approver console. Loading, empty and error states throughout.
- **Validation and errors**: Bean Validation on every input, RFC 7807 problem responses, optimistic locking, and a database-level unique index that allows only one pending refund per order.

## Architecture

```mermaid
flowchart LR
    subgraph UI[Angular + Tailwind]
        CHAT[Customer chat]
        APPR[Approver console]
    end

    subgraph API[Spring Boot backend]
        CC[ChatController]
        CS[ChatService<br/>Spring AI ChatClient]
        MEM[MessageChatMemoryAdvisor]
        QA[QuestionAnswerAdvisor]
        TOOLS[SupportTools<br/>@Tool methods]
        OS[OrderService]
        RS[RefundService<br/>policy checks]
        RC[RefundController<br/>X-Approver-Key]
        KB[KnowledgeBaseService<br/>startup loader]
    end

    subgraph DB[PostgreSQL]
        ORD[(orders, order_items)]
        REF[(refund_requests)]
        VS[(vector_store<br/>pgvector)]
    end

    LLM[[ChatModel + EmbeddingModel<br/>Ollama · OpenAI · Gemini]]

    CHAT -- "POST /api/chat" --> CC --> CS
    CS --> MEM
    CS --> QA -- similarity search --> VS
    CS -- prompt + tool definitions --> LLM
    LLM -- tool calls --> TOOLS
    TOOLS -- "customer from ToolContext" --> OS --> ORD
    TOOLS -- "requestRefund → PENDING" --> RS --> REF
    APPR -- "approve / reject" --> RC --> RS
    RS -- "approved: apply refund" --> ORD
    KB -- "embed Markdown sections" --> LLM
    KB --> VS
```

**A refund, end to end**

1. The customer writes *"I want a refund for ORD-1001, the left earbud doesn't charge."*
2. The model calls `requestRefund(orderId, reason)`. The tool takes the customer's email from the `ToolContext`, and `RefundService` checks ownership, status, the 30-day window, the amount and existing pending requests.
3. A `refund_requests` row is stored with status `PENDING`. The agent tells the customer the request is waiting for review, and the UI shows a "pending" card.
4. An approver opens **Approvals**, reviews the request and approves it (optional note) or rejects it (note required).
5. On approval, the order's refunded amount is updated (and the status becomes `REFUNDED` when fully refunded). The customer sees the decision in their sidebar, or by asking the agent ("what's the status of my refund?").

## Tech stack

| Layer | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, Bean Validation, Flyway, Actuator |
| AI | Spring AI 1.1: ChatClient, advisors (chat memory, QuestionAnswer RAG), `@Tool` tool calling, pgvector `VectorStore`; Ollama, OpenAI and Google GenAI starters |
| Database | PostgreSQL 14+ with the pgvector extension |
| Frontend | Angular 21 (standalone components, signals, zoneless), Tailwind CSS 4 |
| Tests | JUnit 5, Mockito, Spring MockMvc, H2 (context test); Vitest with Angular TestBed |
| Optional | Docker, Docker Compose |

## Project structure

```
supportpilot-spring-ai/
├── backend/
│   ├── src/main/java/com/supportpilot/
│   │   ├── agent/          # ChatController, ChatService (ChatClient), SupportTools (@Tool), AgentAction
│   │   ├── refund/         # RefundRequest entity, RefundService (policy + approval), RefundController
│   │   ├── order/          # Order entity, OrderService, OrderController, OrderSeeder
│   │   ├── knowledge/      # KnowledgeBaseService (startup loader), MarkdownSectionSplitter
│   │   ├── config/         # AiConfig (ChatClient + advisors), properties, approver key, CORS, /api/info
│   │   └── common/         # exceptions and RFC 7807 error handling
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/   # Flyway schema
│   │   ├── knowledge/      # sample knowledge-base articles (Markdown)
│   │   └── seed/orders.json
│   ├── src/test/           # unit, MockMvc and full-context tests
│   ├── .env.example
│   ├── Dockerfile
│   └── mvnw, mvnw.cmd      # Maven wrapper (no Maven install needed)
├── frontend/
│   ├── src/app/
│   │   ├── chat/           # customer chat page
│   │   ├── approvals/      # approver console
│   │   ├── api.service.ts  # typed HTTP client
│   │   └── models.ts
│   ├── proxy.conf.json     # dev proxy /api → :8080
│   ├── Dockerfile + nginx.conf
│   └── package.json
├── docker-compose.yml
└── .github/workflows/ci.yml
```

## Prerequisites

- **JDK 21 or newer** (the Maven wrapper downloads Maven itself)
- **Node.js 20.19+ or 22.12+** with npm (for Angular 21)
- **PostgreSQL 14+** with **pgvector**
- **Ollama** (https://ollama.com) with a chat model that supports tool calling and an embedding model, *or* an OpenAI / Gemini API key
- Optional: Docker with Docker Compose

## Local setup (without Docker)

### 1. Ollama models

```bash
ollama pull qwen2.5:7b          # chat model with tool-calling support
ollama pull nomic-embed-text    # 768-dimensional embeddings
```

Other tool-capable models such as `llama3.1:8b` also work: set `OLLAMA_CHAT_MODEL`. Small models may follow the refund instructions less reliably.

### 2. PostgreSQL + pgvector

Install PostgreSQL and pgvector:

- **Windows**: install PostgreSQL 16 from https://www.postgresql.org/download/windows/, then build pgvector from the *x64 Native Tools Command Prompt for VS* (as Administrator), as described in https://github.com/pgvector/pgvector#windows:

  ```bat
  set "PGROOT=C:\Program Files\PostgreSQL\16"
  cd %TEMP%
  git clone --branch v0.8.0 https://github.com/pgvector/pgvector.git
  cd pgvector
  nmake /F Makefile.win
  nmake /F Makefile.win install
  ```

- **Linux (Debian/Ubuntu)**: `sudo apt install postgresql postgresql-16-pgvector`. Use the package for your PostgreSQL major version, from the PGDG apt repository.
- **macOS**: `brew install postgresql@17 pgvector && brew services start postgresql@17`. Check `brew info pgvector` for supported versions.

Create the database and extensions as a superuser (`psql -U postgres`):

```sql
CREATE USER supportpilot WITH PASSWORD 'supportpilot';
CREATE DATABASE supportpilot OWNER supportpilot;
\c supportpilot
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

Spring AI's pgvector store uses those three extensions. Flyway creates the application tables, and the vector table is created on first start.

### 3. Backend

**Windows (PowerShell)**

```powershell
cd backend
copy .env.example .env      # optional; every value has a default
.\mvnw.cmd spring-boot:run
```

**Linux / macOS**

```bash
cd backend
cp .env.example .env        # optional
./mvnw spring-boot:run
```

The API runs on http://localhost:8080. At startup it:

- applies the Flyway migration,
- seeds the sample orders (only if the orders table is empty),
- embeds the knowledge base (only files that changed since the last run),
- prints the **approver key** if `APPROVER_API_KEY` is not set. Look for `Generated approver key for this run:` in the log.

### 4. Frontend

```bash
cd frontend
npm install
npm start
```

Open http://localhost:4200. The dev server proxies `/api` to `http://localhost:8080`.

## Running with Docker (optional)

```bash
# Uses Ollama running on your machine (pull the models first)
docker compose up --build

# Or run Ollama in a container as well
OLLAMA_BASE_URL=http://ollama:11434 docker compose --profile ollama up --build
docker compose exec ollama ollama pull qwen2.5:7b
docker compose exec ollama ollama pull nomic-embed-text
```

On Windows PowerShell, set the variable first: `$env:OLLAMA_BASE_URL="http://ollama:11434"`.

- UI: http://localhost:4200
- API: http://localhost:8080
- Approver key: `docker compose logs backend | grep "approver key"` (or set `APPROVER_API_KEY` before starting)

The `pgvector/pgvector` image already contains the needed extensions. On Linux, a host Ollama must listen on all interfaces (`OLLAMA_HOST=0.0.0.0`) to be reachable from containers.

## Try it

Pick a customer at the top left of the chat, then try:

| Customer | Message | Expected behaviour |
|---|---|---|
| Alice | "Where is my order ORD-1002?" | `trackOrder` returns carrier, tracking number and ETA |
| Alice | "I want a refund for ORD-1001, the left earbud doesn't charge" | A `PENDING` refund request is created and appears in **Approvals** |
| Alice | "Can I get a refund for ORD-1003?" | Refused by policy: delivered 45 days ago, outside the 30-day window |
| Bob | "Refund my order ORD-1004" | Refused: the order is still processing |
| Bob | "Show me Alice's order ORD-1001" | Not found: tools only see the signed-in customer's orders |
| Carol | "My camera ORD-1008 is late, what can I do?" | Order lookup plus the late-delivery policy from the knowledge base |
| Any | "What's your warranty on laptops?" | Answered from `warranty.md`, with the source shown |

Then open **Approvals**, enter your name and the approver key, and approve or reject the request.

Sample customers: `alice@example.com`, `bob@example.com`, `carol@example.com`.

## Switching model providers

Set environment variables (or edit `backend/.env`) and restart. No code changes are needed.

```ini
# OpenAI for chat and embeddings
AI_CHAT_PROVIDER=openai
AI_EMBEDDING_PROVIDER=openai
OPENAI_API_KEY=sk-...
EMBEDDING_DIMENSIONS=768      # text-embedding-3-small is asked for 768-dimensional vectors

# Gemini for chat (embeddings stay on Ollama)
AI_CHAT_PROVIDER=google-genai
GOOGLE_API_KEY=...
```

Gemini embeddings go through Gemini's OpenAI-compatible endpoint, using the OpenAI embedding client:

```ini
AI_EMBEDDING_PROVIDER=openai
OPENAI_EMBEDDING_API_KEY=<your Gemini API key>
OPENAI_EMBEDDING_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
OPENAI_EMBEDDINGS_PATH=/embeddings
OPENAI_EMBEDDING_MODEL=gemini-embedding-001
EMBEDDING_DIMENSIONS=3072
PGVECTOR_INDEX_TYPE=NONE      # HNSW indexes support at most 2000 dimensions
```

(The native `spring-ai-starter-model-google-genai-embedding` is intentionally not used. In Spring AI 1.1 its connection auto-configuration requires Google credentials even when Gemini embeddings are not selected, which would stop the app from starting without a key.)

**Changing the embedding model or its dimensions** requires a fresh vector table. Stop the app and run `DROP TABLE vector_store; TRUNCATE knowledge_sources;`. The knowledge base is re-embedded on the next start.

## Environment variables

All variables are optional. See [`backend/.env.example`](backend/.env.example).

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/supportpilot` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `supportpilot` / `supportpilot` | Database credentials |
| `AI_CHAT_PROVIDER` | `ollama` | `ollama`, `openai` or `google-genai` |
| `AI_EMBEDDING_PROVIDER` | `ollama` | `ollama` or `openai` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server |
| `OLLAMA_CHAT_MODEL` | `qwen2.5:7b` | Ollama chat model (must support tools) |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Ollama embedding model |
| `OPENAI_API_KEY` | none | Required only for the OpenAI provider |
| `OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI-compatible endpoint |
| `OPENAI_CHAT_MODEL` | `gpt-4o-mini` | OpenAI chat model |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | OpenAI embedding model |
| `OPENAI_EMBEDDING_API_KEY` / `OPENAI_EMBEDDING_BASE_URL` / `OPENAI_EMBEDDINGS_PATH` | none / none / `/v1/embeddings` | Separate settings for the embedding client (e.g. Gemini) |
| `GOOGLE_API_KEY` | none | Required only for `AI_CHAT_PROVIDER=google-genai` |
| `GEMINI_CHAT_MODEL` | `gemini-2.5-flash` | Gemini chat model |
| `EMBEDDING_DIMENSIONS` | `768` | Vector size, must match the embedding model |
| `PGVECTOR_INDEX_TYPE` | `HNSW` | `HNSW`, `IVFFLAT` or `NONE` |
| `APPROVER_API_KEY` | generated at startup | Key for the approver API and console |
| `RAG_TOP_K` | `4` | Knowledge-base sections retrieved per question |
| `RAG_SIMILARITY_THRESHOLD` | `0.4` | Minimum similarity for a section to be used |
| `REFUND_WINDOW_DAYS` | `30` | Refund window after delivery |
| `CORS_ORIGINS` | `http://localhost:4200` | Allowed browser origins (comma-separated) |
| `PORT` | `8080` | HTTP port |
| `AI_LOG_LEVEL` | `INFO` | `DEBUG` logs every prompt and response |

## API endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/chat` | none (demo) | Send a message: `{conversationId, customerEmail, customerName, message}` → `{reply, actions[], sources[]}` |
| `DELETE` | `/api/chat/{conversationId}?email=` | none (demo) | Clear a conversation's memory |
| `GET` | `/api/customers` | none | Sample customers |
| `GET` | `/api/orders?email=` | none (demo) | A customer's orders |
| `GET` | `/api/orders/{id}?email=` | none (demo) | One order (`404` if it belongs to someone else) |
| `GET` | `/api/refunds/mine?email=` | none (demo) | A customer's refund requests |
| `GET` | `/api/refunds?status=PENDING` | `X-Approver-Key` | Approver queue (`status` optional) |
| `POST` | `/api/refunds/{id}/approve` | `X-Approver-Key` | `{approverName, note?}` → approved request (`409` if already decided) |
| `POST` | `/api/refunds/{id}/reject` | `X-Approver-Key` | `{approverName, note}` → rejected request |
| `GET` | `/api/info` | none | Active providers and models, knowledge-base status |
| `POST` | `/api/admin/knowledge/reload` | `X-Approver-Key` | Retry or refresh knowledge-base loading |
| `GET` | `/actuator/health` | none | Health check |

Errors use RFC 7807 problem details, for example `{"status": 409, "title": "Conflict", "detail": "Order ORD-1003 was delivered more than 30 days ago, which is outside the refund window"}`. If the AI provider cannot be reached, `/api/chat` returns `503` with the reason.

Example:

```bash
curl -X POST http://localhost:8080/api/chat -H "Content-Type: application/json" -d '{
  "conversationId": "3f1c1b9e-1111-4c4c-9a9a-123456789abc",
  "customerEmail": "alice@example.com",
  "customerName": "Alice Johnson",
  "message": "Where is ORD-1002?"
}'
```

## Security notes

This is a demo of the agent pattern, not a complete authentication setup:

- **Customer identity** comes from the UI's customer picker and is sent as `customerEmail`. In production, derive it from the authenticated session (for example Spring Security with OAuth2) and keep passing it to tools through the `ToolContext`, never through the prompt.
- **Approver access** is a shared key in the `X-Approver-Key` header, compared in constant time. If none is configured, a random key is generated for each run. Use per-user accounts and roles in production.
- The approval endpoints are the only code path that changes refund status or order balances. The model has no tool that can approve anything.

## Testing

```bash
# Backend: 50 tests (unit, MockMvc and a full Spring context on H2; no database, Ollama or keys needed)
cd backend
./mvnw test           # Windows: .\mvnw.cmd test

# Frontend: Vitest + Angular TestBed
cd frontend
npx ng test --watch=false
npx ng build
```

What is covered:

- **Refund policy**: delivered-only, 30-day window boundaries, partial amounts and rounding, balance limits, required reason, duplicate pending requests (including the database race), approval and rejection effects, final decisions.
- **Tools**: the customer comes from the `ToolContext`, problems are returned as results, actions are recorded, and the generated tool schemas don't expose the context.
- **Chat service**: system prompt contents, provider failures mapped to `503`, extraction of retrieved sources.
- **Knowledge base**: Markdown section splitting, stable document ids, unchanged files skipped, changed files replaced, embedding failures reported instead of crashing.
- **Controllers**: approver key enforcement, validation, error mapping.
- **Application context**: boots with default settings and **no API keys**, with exactly one (Ollama) chat model and embedding model, the seeded sample data and the info endpoint.
- **Frontend**: API client (headers, error mapping, HTML escaping), chat flow (sending, typing indicator, pending-refund card, sources, error and retry), approver console (key prompt, approve, note required to reject, invalid key).
