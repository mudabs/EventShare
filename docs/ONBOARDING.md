# Developer Onboarding

## Prerequisites

JDK 25, Maven 3.9+, Node.js 22+, Docker and Docker Compose. A Clerk test application and an
R2 bucket for full functionality, though much of the code runs against local defaults.

## Local setup

Start dependencies and the three services:

```bash
docker compose up -d postgres rabbitmq
cd backend && mvn spring-boot:run            # API on :8080
cd worker  && mvn spring-boot:run            # worker on :8081
cd frontend && cp .env.local.example .env.local && npm install && npm run dev   # :3000
```

The API and worker default to localhost PostgreSQL and RabbitMQ (see each `application.yml`).
Set R2_* and CLERK_* in your shell or an env file to exercise uploads and auth end to end.

## Project conventions

Backend is a modular monolith: one package per domain (`user`, `event`, `media`, `audit`)
with controller, service, repository, and DTOs; shared concerns under `common`. Services hold
business logic and authorization; controllers stay thin. DTOs are Java records. Entities
extend `BaseEntity` for UUID identity and auditing. Enumerations are stored as strings.

Frontend uses the Next.js App Router. Server components by default; client components are
marked with `'use client'`. Data fetching goes through React Query; cross-cutting client
state (guest identity) lives in a Zustand store. API access is centralized in `src/lib/api.ts`.

## How to add a database change

Create a new migration `backend/src/main/resources/db/migration/V<n>__<description>.sql`.
Never edit an applied migration. Run `mvn -q -pl backend test` (the Testcontainers
integration test validates entity mappings against the migrated schema). Update `docs/ERD.md`.

## How to add an API endpoint

Add or extend the DTOs, implement the logic in the domain service with the authorization
check, expose it in the controller, and update `docs/API.md`. If the endpoint is a guest
capability endpoint, add its path to the permit list in `SecurityConfig` and enforce the
invite-code check in the service.

## Testing

```bash
cd backend && mvn verify     # unit (surefire) + Testcontainers integration (failsafe)
cd worker  && mvn test
cd frontend && npm run typecheck && npm run build
```

Unit tests mock collaborators and run without Docker. Integration tests spin up PostgreSQL
via Testcontainers and need Docker available. CI runs all of this on every push.

## Code style

Prefer clear, explicit code over cleverness. Keep methods small and named for intent.
Validate inputs at the boundary. Return RFC 7807 problem responses through the existing
exception types rather than ad hoc error bodies. Add a test with any non-trivial change.
