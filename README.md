# Autoflow

> A self-hostable workflow automation engine and AI-assisted job application tracker.

Autoflow is a privacy-first alternative to tools like Zapier — built for teams and individuals who don't want their data passing through third-party SaaS platforms. Connect your tools, define trigger → action workflows, and let the engine handle the rest.

On top of the automation engine, Autoflow includes a personal job tracker with an AI-assisted application pipeline: ingest job listings, analyze fit, tailor your resume, draft cover letters and cold emails, and track every application from first look to offer.

---

## Table of Contents

- [Why Autoflow](#why-autoflow)
- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [Services](#services)
  - [Auth Service](#auth-service)
  - [Workflow Engine](#workflow-engine)
  - [GitHub Worker](#github-worker)
  - [Gmail Worker](#gmail-worker)
  - [AI Worker](#ai-worker)
- [Database Schema](#database-schema)
- [Kafka Topics](#kafka-topics)
- [API Reference](#api-reference)
- [Job Tracker Feature](#job-tracker-feature)
- [AI Pipeline](#ai-pipeline)
- [Development Roadmap](#development-roadmap)
- [Contributing](#contributing)

---

## Why Autoflow

The workflow automation market is dominated by cloud-only SaaS products. For teams handling sensitive data — resumes, credentials, internal communications — routing everything through a third-party platform is a real risk.

Autoflow is designed to run entirely on your own infrastructure. Your data never leaves your stack.

**Key differentiators:**
- Fully self-hostable via Docker Compose — one command to run everything
- Event-driven architecture using Kafka — durable, replayable, decoupled
- Multi-tenant from day one — row-level security enforced at both query and DB layer
- Human-in-the-loop AI pipeline — the agent does the cognitive work, you approve before anything irreversible happens
- Privacy-first job tracking — your resume, applications, and OAuth tokens stay on your machine

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        Client / Browser                      │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP
          ┌─────────────────▼──────────────────┐
          │           Auth Service              │
          │         (Java / Spring Boot)        │
          │   OAuth2 · JWT · Token Management   │
          └─────────────────┬──────────────────┘
                            │
          ┌─────────────────▼──────────────────┐
          │         Workflow Engine             │
          │         (Java / Spring Boot)        │
          │  Trigger Evaluation · Scheduling    │
          └────┬──────────────────────┬─────────┘
               │  Kafka               │ Kafka
    ┌──────────▼───────┐   ┌─────────▼──────────┐
    │  Integration     │   │    AI Worker        │
    │  Workers         │   │  (Python/FastAPI)   │
    │  (Python/FastAPI)│   │  LLM · Resume       │
    │                  │   │  JD Parsing         │
    │  • GitHub        │   └─────────────────────┘
    │  • Gmail         │
    └──────────────────┘
               │
    ┌──────────▼────────────────────────────────┐
    │              Infrastructure                │
    │  PostgreSQL · Redis · Kafka · MinIO        │
    └────────────────────────────────────────────┘
```

**Event flow:**
1. An external event arrives (GitHub commit, Gmail message, manual job entry)
2. The relevant worker validates, deduplicates, and publishes to Kafka
3. The workflow engine consumes the event, evaluates matching workflows, and publishes action events
4. Action workers execute the action (send email, update DB, trigger AI pipeline)
5. Results are written to PostgreSQL and surfaced on the dashboard

---

## Tech Stack

| Layer | Technology | Rationale |
|---|---|---|
| Backend services | Java 21 + Spring Boot 3 | Strong OAuth2/security ecosystem, familiar and battle-tested |
| Integration workers | Python 3.13 + FastAPI | Fast iteration on webhook handlers, broad library support |
| AI worker | Python 3.13 + FastAPI | LangChain / OpenAI SDK ecosystem lives here |
| Event bus | Apache Kafka | Durable, replayable events; partition by `user_id` for ordering |
| Primary database | PostgreSQL 16 | JSONB for flexible configs; pgvector for semantic search later |
| Cache / idempotency | Redis 7 | Hot-path dedup checks, token caching, rate limit counters |
| Object storage | MinIO | S3-compatible, fully self-hostable — resumes and documents |
| Frontend | React + TypeScript | Job tracker dashboard and workflow builder UI |
| Container runtime | Docker + Docker Compose | Single-command infrastructure boot for self-hosting |

---

## Project Structure

```
autoflow/
│
├── docker-compose.yml          # Spins up all infrastructure services
├── .env                        # Local secrets — never committed to git
├── .env.example                # Template with all required keys (no real values)
├── README.md
│
├── infrastructure/
│   ├── postgres/
│   │   └── init.sql            # Full schema — runs automatically on first boot
│   ├── kafka/
│   │   └── create-topics.sh   # Creates all Kafka topics with correct partitioning
│   └── redis/
│       └── redis.conf          # Redis configuration
│
├── services/
│   ├── auth-service/           # Java/Spring Boot — OAuth2, JWT, user management
│   ├── workflow-engine/        # Java/Spring Boot — workflow CRUD, trigger evaluation
│   └── workers/
│       ├── github-worker/      # Python/FastAPI — GitHub webhooks, repo polling
│       ├── gmail-worker/       # Python/FastAPI — Gmail OAuth, send/receive
│       └── ai-worker/          # Python/FastAPI — LLM calls, resume parsing, JD analysis
│
├── frontend/                   # React — job tracker dashboard, workflow builder
│
└── docs/
    ├── architecture.md         # Detailed architecture decisions and diagrams
    └── api-spec.md             # Full REST API specification
```

---

## Prerequisites

The following must be installed on your machine before running Autoflow:

| Tool | Version | Purpose |
|---|---|---|
| Docker Desktop | Latest | Runs all infrastructure (Postgres, Kafka, Redis, MinIO) |
| Java | 21 (LTS) | Compiles and runs Spring Boot services locally |
| Python | 3.12+ | Runs FastAPI workers locally during development |
| Node.js | 20 LTS | Builds and runs the React frontend |
| Maven | 3.9+ | Java build tool |
| Git | Any recent | Version control |

> **Note:** You do not need to install PostgreSQL, Redis, Kafka, or MinIO directly. They run entirely inside Docker containers.

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/autoflow.git
cd autoflow
```

### 2. Set up your environment

```bash
cp .env.example .env
```

Open `.env` and replace all placeholder values with real credentials. See [Environment Variables](#environment-variables) for a full description of each key.

### 3. Start the infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL, Redis, Zookeeper, Kafka, and MinIO. The database schema is applied automatically on first boot via `infrastructure/postgres/init.sql`.

### 4. Create Kafka topics

```bash
docker cp infrastructure/kafka/create-topics.sh autoflow-kafka:/tmp/create-topics.sh
docker exec -it autoflow-kafka bash /tmp/create-topics.sh
```

### 5. Verify everything is healthy

```bash
# PostgreSQL
docker exec -it autoflow-postgres pg_isready -U autoflow_user

# Redis
docker exec -it autoflow-redis redis-cli ping
# Expected: PONG

# Kafka topics
docker exec -it autoflow-kafka kafka-topics --list --bootstrap-server localhost:9092

# MinIO console
open http://localhost:9001
```

### 6. Start the auth service

```bash
cd services/auth-service
mvn spring-boot:run
```

### 7. Start the workflow engine

```bash
cd services/workflow-engine
mvn spring-boot:run
```

### 8. Start a worker (example: GitHub worker)

```bash
cd services/workers/github-worker
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8083
```

---

## Environment Variables

All configuration lives in `.env` at the project root. Copy `.env.example` to get started — never commit your actual `.env` file.

| Variable | Description | Example |
|---|---|---|
| `POSTGRES_DB` | Database name | `autoflow` |
| `POSTGRES_USER` | Database username | `autoflow_user` |
| `POSTGRES_PASSWORD` | Database password | `strong_password` |
| `POSTGRES_PORT` | Host port for PostgreSQL | `5432` |
| `REDIS_PORT` | Host port for Redis | `6379` |
| `KAFKA_PORT` | Host port for Kafka broker | `9092` |
| `ZOOKEEPER_PORT` | Host port for Zookeeper | `2181` |
| `MINIO_ROOT_USER` | MinIO admin username | `minioadmin` |
| `MINIO_ROOT_PASSWORD` | MinIO admin password | `strong_password` |
| `MINIO_API_PORT` | MinIO S3 API port | `9000` |
| `MINIO_CONSOLE_PORT` | MinIO web console port | `9001` |
| `AUTH_SERVICE_PORT` | Port the auth service listens on | `8081` |
| `WORKFLOW_ENGINE_PORT` | Port the workflow engine listens on | `8082` |
| `JWT_SECRET` | Secret for signing JWTs — use a long random string | — |

> Additional variables for OAuth2 provider credentials (GitHub, Google) will be added as those integrations are built out.

---

## Services

### Auth Service

**Port:** `8081` | **Language:** Java 21 / Spring Boot 3

Responsible for all authentication and authorization concerns:
- Google and GitHub OAuth2 login flows
- JWT issuance and validation
- OAuth token storage and refresh
- User account management

> **Status:** 🔧 In progress

---

### Workflow Engine

**Port:** `8082` | **Language:** Java 21 / Spring Boot 3

The core of the automation platform:
- CRUD for workflow definitions (trigger config + action config stored as JSONB)
- Consumes events from `workflow.trigger.events` and evaluates matching workflows
- Publishes matched action events to `workflow.action.events`
- Manages execution history and correlation IDs for end-to-end tracing
- Handles retry logic via `workflow.action.retries`

> **Status:** 🔧 In progress

---

### GitHub Worker

**Port:** `8083` | **Language:** Python 3.12 / FastAPI

Handles all GitHub integration:
- Receives inbound GitHub webhooks (HMAC-SHA256 validated)
- Polls configured repositories for new commits (e.g. SimplifyJobs job listing repos)
- Parses structured job listing data from repo contents
- Deduplicates events using Redis + `processed_events` table
- Publishes to `job.intake.events` and `workflow.trigger.events`

> **Status:** 🔧 In progress

---

### Gmail Worker

**Port:** `8084` | **Language:** Python 3.12 / FastAPI

Handles all Gmail integration:
- Gmail OAuth2 flow via stored tokens
- Sends cold outreach emails drafted by the AI worker
- Monitors inbox for application-related replies (future)
- Rate limiting enforced via Redis counters

> **Status:** 📋 Planned

---

### AI Worker

**Port:** `8085` | **Language:** Python 3.12 / FastAPI

Orchestrates the AI-assisted job application pipeline:
- Parses job descriptions into structured data (role, skills, requirements)
- Analyzes resume against job description — produces a match score and gap analysis
- Tailors resume content for a specific role (human approval required before saving)
- Drafts cover letters and cold outreach emails
- Logs all LLM calls to `agent_run_logs` — model, token count, input, output

Human-in-the-loop: the AI worker pauses at defined checkpoints and publishes to `agent.human.checkpoints`. No irreversible action (sending email, submitting application) proceeds without explicit user approval.

> **Status:** 📋 Planned

---

## Database Schema

All tables are created automatically by `infrastructure/postgres/init.sql` on first boot.

| Table | Purpose |
|---|---|
| `users` | User accounts |
| `oauth_tokens` | OAuth2 access/refresh tokens per provider per user |
| `workflows` | Workflow definitions — trigger and action configs stored as JSONB |
| `workflow_executions` | Execution history — status, payload, result, correlation ID |
| `processed_events` | Idempotency log — prevents duplicate event processing |
| `job_listings` | Ingested job postings with raw and parsed description |
| `applications` | Application tracking — status, applied date, notes |
| `resume_versions` | Versioned resumes with parsed content — stored in MinIO |
| `cover_letters` | Generated or manual cover letters per application |
| `agent_run_logs` | Full audit trail of every AI pipeline step |

Multi-tenant isolation is enforced via `user_id` foreign keys on every table. All queries filter by the authenticated user's ID, and row-level security policies are enforced at the database layer as a second line of defense.

---

## Kafka Topics

All topics use 6 partitions, keyed by `user_id` to preserve per-user event ordering.

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `workflow.trigger.events` | Integration workers | Workflow engine | Inbound events that may trigger a workflow |
| `workflow.action.events` | Workflow engine | Integration workers | Actions to be executed by workers |
| `workflow.action.retries` | Workflow engine | Workflow engine | Failed actions queued for exponential backoff retry |
| `job.intake.events` | GitHub worker | AI worker | New job listings ready for parsing and analysis |
| `agent.pipeline.events` | AI worker | AI worker | Internal steps of the AI application pipeline |
| `agent.human.checkpoints` | AI worker | Frontend / user | Pipeline paused — waiting for human approval |

---

## API Reference

> Full API specification is in `docs/api-spec.md`. A summary of key endpoints is below.

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/auth/login/github` | Initiate GitHub OAuth2 flow |
| `GET` | `/auth/login/google` | Initiate Google OAuth2 flow |
| `GET` | `/auth/callback/{provider}` | OAuth2 callback handler |
| `POST` | `/auth/refresh` | Refresh a JWT using a refresh token |
| `POST` | `/auth/logout` | Revoke tokens and clear session |

### Workflows

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/workflows` | List all workflows for the authenticated user |
| `POST` | `/workflows` | Create a new workflow |
| `GET` | `/workflows/{id}` | Get a single workflow |
| `PUT` | `/workflows/{id}` | Update a workflow |
| `DELETE` | `/workflows/{id}` | Delete a workflow |
| `GET` | `/workflows/{id}/executions` | Paginated execution history |

### Job Tracker

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/jobs` | List all ingested job listings |
| `POST` | `/jobs` | Manually add a job listing |
| `GET` | `/applications` | List all applications with status |
| `POST` | `/applications` | Create an application for a job listing |
| `PATCH` | `/applications/{id}/status` | Update application status |

### Webhooks (inbound)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/webhooks/github` | Inbound GitHub webhook (HMAC-SHA256 validated) |

---

## Job Tracker Feature

Autoflow includes a personal job application tracker powered by the same event-driven architecture as the automation engine.

**How job listings get in:**
- **GitHub repo polling** — monitors community-maintained repos like `SimplifyJobs/New-Grad-Positions` for new commits. New rows are parsed and ingested automatically.
- **Manual entry** — paste a job URL or description directly into the dashboard.
- *(Planned)* **Browser extension** — one-click capture from any job board.

**Application statuses:**
`saved` → `applied` → `interview` → `offer` / `rejected`

**Provenance tracking:** Every ingested listing stores its source repo, commit SHA, and external row ID so you always know where a listing came from and can detect upstream changes.

---

## AI Pipeline

The AI worker implements a human-in-the-loop pipeline for job applications. Every step that produces output pauses for your review before proceeding.

```
Job Listing Ingested
        │
        ▼
  [STEP 1] Parse Job Description
  → Extracts: role, company, required skills, nice-to-haves, seniority
        │
        ▼
  [STEP 2] Resume Match Analysis
  → Produces: match score, skill gaps, talking points
        │
        ▼
  [CHECKPOINT] ← Human reviews analysis, decides to apply
        │
        ▼
  [STEP 3] Resume Tailoring Suggestions
  → Produces: recommended edits to base resume for this role
        │
        ▼
  [CHECKPOINT] ← Human reviews and approves tailored resume
        │
        ▼
  [STEP 4] Cover Letter Draft
  → Produces: personalized cover letter
        │
        ▼
  [CHECKPOINT] ← Human reviews and approves cover letter
        │
        ▼
  [STEP 5] Cold Email Draft (optional)
  → Produces: outreach email to hiring manager or recruiter
        │
        ▼
  [CHECKPOINT] ← Human approves — Gmail worker sends email
```

All LLM calls are logged in `agent_run_logs` with model name, token count, and full input/output for auditability.

---

## Development Roadmap

### Phase 1 — Foundation ✅ In Progress
- [x] Project scaffold and folder structure
- [x] Docker Compose infrastructure (Postgres, Redis, Kafka, MinIO)
- [x] Database schema
- [x] Kafka topic creation
- [ ] Auth service — OAuth2 login with GitHub and Google
- [ ] JWT issuance and validation middleware
- [ ] Workflow engine — basic CRUD

### Phase 2 — Job Tracker
- [ ] Job listings API and dashboard UI
- [ ] GitHub worker — SimplifyJobs repo polling
- [ ] Manual job entry and status tracking
- [ ] Application status board

### Phase 3 — AI Analysis
- [ ] AI worker bootstrap
- [ ] JD parsing pipeline
- [ ] Resume upload and versioning (MinIO)
- [ ] Resume-to-JD match scoring

### Phase 4 — Draft Generation
- [ ] Cover letter generation
- [ ] Cold email drafting
- [ ] Gmail worker — send approved emails
- [ ] Human-in-the-loop checkpoint UI

### Phase 5 — Automation Engine
- [ ] Workflow builder UI
- [ ] GitHub webhook trigger support
- [ ] Full trigger → action pipeline
- [ ] Retry logic and execution history view

### Phase 6 — Polish & Self-Hosting
- [ ] Browser extension for one-click JD capture
- [ ] Docker Compose production profile
- [ ] Helm chart for Kubernetes deployment
- [ ] Full self-hosting documentation

---

## Contributing

This project is currently in early development. Contribution guidelines will be added once the foundation is stable.

If you find a bug or have a feature suggestion, please open an issue.

---
> **Last updated:** March 2026 | Phase 1 — Foundation