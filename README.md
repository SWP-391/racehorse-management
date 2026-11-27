# Racehorse Training & Management System

A web application for managing a racehorse training club: horses, training plans and sessions, health records, stable areas, staff schedules, stock and medicine, and owner billing.

## Tech stack

| Part | Stack | Location |
|---|---|---|
| Backend service | Spring Boot 3, Java 21, PostgreSQL 16, Flyway | `backend/` |
| Web client | React 18, TypeScript, Vite, Tailwind CSS 4 (Vietnamese UI) | `frontend/` |

## Features

- **Accounts & roles** — Club Manager, Head Trainer, Veterinarian, Groom, Horse Owner, each with a tailored home page and permissions.
- **Horses** — profiles, photo, stable placement, assigned trainer, ownership.
- **Training** — plans and sessions per horse, scheduling, responsible Groom hand-over, trial-run videos, live session monitoring.
- **Health** — examinations, vaccination/deworming, injury marks on an interactive 3D horse model, approved feeding rations.
- **Stable operations** — stable areas and stalls, Groom barn hours and leave, daily chores.
- **Stock & medicine** — supply inventory with a stock ledger, restock forecasting, low-stock alerts.
- **Billing** — service price list, per-horse cost tracking, monthly owner invoices.
- **Hộp thư (inbox)** — conversations between the roles that work together, for example an owner sending feedback or a request to the Club Manager. Text only, never edited or deleted; each new message gives the others an in-app notification and an unread count. Who may write to whom: every role to the Club Manager; owner with the trainer of their horse and with a veterinarian who examined it; veterinarian with the trainer of a horse; groom with a head trainer.

## Prerequisites

- Docker (Desktop) — the service is built and tested in a JDK 21 container, so no local Java or Maven is needed.
- Node.js 22 for the web client.

## Run locally

```bash
# 1. Build the service jar (Maven runs in a container)
scripts/backend.sh -q package -DskipTests

# 2. Start PostgreSQL, the service and the web client
BOOTSTRAP_EMAIL='admin@racehorse.example' BOOTSTRAP_PASSWORD='choose-a-password' docker compose --profile app up -d

# 3. Open http://localhost:5180 and sign in with that email and password
```

The first Club Manager account is created at first start-up from `BOOTSTRAP_EMAIL` / `BOOTSTRAP_PASSWORD`; the email is what the person signs in with. Other accounts are created by a Club Manager afterwards, each with one email. By default the Club Manager gives no password: the account waits as "Chờ kích hoạt" and the person activates it on the page `/activate` with a code (valid 24 hours) mailed to that email; the Club Manager can resend the code or correct the email. The Club Manager can also rename any account, their own included, with the pencil button "Sửa tên" on the accounts page (`PUT /api/accounts/{id}/name`); the change is written to the audit log with the old and the new name. "Đặt mật khẩu ngay" keeps the old way for accounts without a mailbox, such as the demo accounts. A forgotten password is recovered with a code sent to that email through the HTTPS API of Brevo (`BREVO_API_KEY`, `MAIL_FROM`, `MAIL_FROM_NAME`).

To run the client outside Docker: `docker compose up -d db`, run the jar with `java -jar backend/target/*.jar` (Java 21), then `cd frontend && npm install && npm run dev`.

Port 5432 already in use on your machine? Stop the other PostgreSQL, or edit the `db` port mapping in `docker-compose.yml`.

## Tests

```bash
scripts/backend.sh test          # service: unit + integration tests (Testcontainers PostgreSQL)
cd frontend && npm test          # web client: component tests (Vitest)
scripts/e2e.sh                   # end-to-end smoke test on a throwaway stack (Playwright in a container)
```

## Deploying

One container serves the web client and the API (root `Dockerfile`, `railway.json`), built for deployment to Railway with managed PostgreSQL and a volume for videos/photos.

## Project layout

```
backend/   Spring Boot service (REST API, business logic, database migrations)
frontend/  React web client
docs/      Design notes, deployment guide, screenshots
scripts/   Helper scripts for building, testing and running the project
```
