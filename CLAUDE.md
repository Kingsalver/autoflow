# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

Autoflow is a **self-hostable workflow automation platform** combining a Zapier-like trigger/action engine with an AI-assisted job application tracker. The project is in **Phase 1 (Foundation)** — infrastructure and auth service are complete; the workflow engine trigger evaluation, all Python workers, and the frontend are stubbed for future implementation.

## Infrastructure

Start all dependencies with:
```bash
docker compose up -d
```

After Kafka is running, create topics:
```bash
bash infrastructure/kafka/create-topics.sh
```

Services and ports (configured via `.env`, see `.env.example`):
- PostgreSQL: 5432
- Redis: 6379
- Kafka: 9092
- MinIO (S3-compatible): API 9000, Console 9001
- Auth Service: 8081
- Workflow Engine: 8082

## Auth Service (Java/Spring Boot)

The auth service also contains the workflow engine package — both run in the same Spring Boot application.

```bash
cd services/auth-service
mvn clean install
mvn spring-boot:run
```

Run tests:
```bash
mvn test
# Single test class:
mvn test -Dtest=ApplicationTests
```

Database schema is owned by `infrastructure/postgres/init.sql`. Spring Boot uses `ddl-auto: validate` — **never use `create` or `update`**; apply schema changes to `init.sql` directly.

## Python Workers

Each worker is a standalone FastAPI app:
```bash
cd services/workers/<worker-name>
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 808X
```
Workers are currently empty stubs — only the directory structure exists.

## Architecture

### Service Boundaries

All services share one PostgreSQL database but are isolated by `user_id`. The auth service and workflow engine share a single Spring Boot JAR but use distinct packages (`com.autoflow.auth`, `com.autoflow.workflow`).

### Multi-Tenancy Pattern

Every entity has a `user_id` column. Repository queries always filter by authenticated user ID (see `WorkflowRepository.findByIdAndUserId()`). The `SecurityContext` is populated by `JwtAuthFilter` reading an HttpOnly cookie.

### Authentication Flow

1. OAuth2 with GitHub via Spring Security (`/auth/login/github` → `/auth/callback/github`)
2. `OAuth2SuccessHandler` upserts `User` + `OAuthToken`, issues JWT pair
3. Access token (15 min) + refresh token (7 days) set as HttpOnly cookies
4. `JwtUtil` uses Redis as a blocklist for revoked tokens
5. `/auth/refresh` rotates both tokens; `/auth/logout` blocklists the access token

### Kafka Event Flow

All topics use `user_id` as the partition key to guarantee per-user ordering.

```
Integration Workers → workflow.trigger.events → Workflow Engine → workflow.action.events → Integration Workers
                                                              ↘ workflow.action.retries (retry queue)

GitHub Worker → job.intake.events → AI Worker → agent.pipeline.events (internal)
                                             ↘ agent.human.checkpoints → Frontend
```

`TriggerEventConsumer` uses **manual acknowledgment** — the offset is committed only after successful processing, so failures cause Kafka replays. The `evaluateMatch()` method is a stub that always returns `false`; implementing it is Phase 5 work.

### JSONB Configuration Pattern

Workflow triggers and actions are stored as JSONB columns (`trigger_config`, `action_config`). The custom `JsonbType` Hibernate type handles serialization. Queries against JSONB use the PostgreSQL `@>` containment operator (see `WorkflowRepository.findEnabledByUserAndTriggerType()`).

### Idempotency

Integration workers deduplicate events via the `processed_events` table (unique `event_key`) backed by Redis caching to avoid redundant DB hits.

## Key Files

| Path | Purpose |
|------|---------|
| `infrastructure/postgres/init.sql` | Authoritative schema for all 10 tables |
| `infrastructure/kafka/create-topics.sh` | Creates all 6 Kafka topics |
| `services/auth-service/application.yml` | Spring Boot config (JWT, OAuth2, DB, Redis) |
| `services/auth-service/src/main/java/com/autoflow/auth/security/` | JWT + OAuth2 security chain |
| `services/auth-service/src/main/java/com/autoflow/workflow/kafka/` | Kafka consumer/producer |
| `services/auth-service/src/main/java/com/autoflow/workflow/service/TriggerEvaluatorService.java` | Stubbed — Phase 5 implementation target |

## Current Implementation Status

**Done:** Docker infrastructure, DB schema, Kafka topics, OAuth2/JWT auth, workflow CRUD, execution history, Kafka plumbing, multi-tenant security.

**Stubbed:** Trigger evaluation logic, all three Python workers (GitHub, Gmail, AI), React frontend, meaningful tests.
