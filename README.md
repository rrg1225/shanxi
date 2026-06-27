# Shanxi Learning Platform

Shanxi Learning Platform is a multi-service education application with a Vue frontend, Spring Boot backend services, and a Python AI gateway for retrieval and generation workflows.

## Project Modules

| Module | Purpose |
| --- | --- |
| `frontend-ui` | Vue 3 learning workspace and knowledge UI |
| `backend-services` | Spring Boot API services, persistence, and platform integrations |
| `ai-gateway` | FastAPI AI gateway for retrieval, generation, and model-provider calls |
| `backend-services/sql` | Database schema and migration-oriented SQL assets |

## Quick Start

Frontend:

```bash
cd frontend-ui
npm install
npm run dev
```

AI gateway:

```bash
cd ai-gateway
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Backend:

```bash
cd backend-services
.\mvnw.cmd -DskipTests package
```

## Quality Notes

- Root-level health check: `python scripts/project_health.py`.
- Keep `.env` files local and use `.env.example` as the public contract.
- Generated frontend builds, Python caches, IDE files, and Maven targets should not be committed.
- The repository currently contains multiple services; each module should be validated before release.

## Enterprise Readiness

This repository now includes contribution guidelines, a security policy, operational runbook notes, PR review gates, and automated readiness checks. See [docs/ENTERPRISE_READINESS.md](docs/ENTERPRISE_READINESS.md) and [docs/OPERATIONS.md](docs/OPERATIONS.md).
