# Notes for AI assistants and new contributors

Read these first, in order:

1. `README.md` for what EventShare is and how to run it.
2. `docs/README.md` for the documentation index.
3. `docs/changes/README.md` for recent changes and the reasoning behind them. The newest
   write-up describes the current state of the upload, delete, quota and processing code.
4. `docs/ARCHITECTURE.md` and `docs/DECISIONS.md` before changing anything structural.
5. `docs/DEMO.md` if the task touches demo mode (seeded accounts and nightly reset).

Conventions:

- Backend: Java 25, Spring Boot 3.5, Maven. `cd backend && mvn verify` runs unit tests and
  Testcontainers integration tests (`*IT.java`, needs Docker).
- Frontend: `cd frontend && npm run typecheck`.
- Database changes go through a new Flyway migration in
  `backend/src/main/resources/db/migration/`; never edit an applied one.
- For any significant change, add a dated file to `docs/changes/` and a row to its README,
  tag code comments with the change id (for example `change C3`; ids restart per write-up, so prefix with the date if ambiguous), and add an ADR to
  `docs/DECISIONS.md` if a design choice was made.
- Never commit secrets. `.env`, `*.env`, `.env.demo`, `frontend/.env.local` and `private/`
  are ignored. Demo passwords are generated into `.env.demo`; never hardcode them.
- Guest authorisation relies on the invite code (view and upload) and the guest's
  membership id (self-delete). Do not reintroduce display-name based checks.
