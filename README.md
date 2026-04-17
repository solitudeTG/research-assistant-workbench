# Research Assistant Phase 1

## Recommended startup

After Docker Desktop is available and `.env` contains a real `AI_DASHSCOPE_API_KEY`, the easiest startup path is:

```powershell
.\scripts\start-dev.cmd
```

PowerShell users can also run:

```powershell
.\scripts\start-dev.ps1
```

The dev start script will:

1. check that Docker is available
2. load `.env`
3. build the application image
4. start PostgreSQL + pgvector and the Spring Boot app with Docker Compose
5. wait until both the database and the application health endpoint are ready

You can also start it directly without the script:

```powershell
docker compose up --build -d
```

Then open [http://localhost:8080](http://localhost:8080).

Stop the project with:

```powershell
.\scripts\stop-dev.cmd
```

## Useful Docker commands

Start:

```powershell
docker compose up --build -d
```

View status:

```powershell
docker compose ps
```

View logs:

```powershell
docker compose logs -f app
```

Stop:

```powershell
docker compose down
```

Stop and remove volumes:

```powershell
docker compose down -v
```

## Environment setup

Copy `.env.example` to `.env` and fill the key:

```powershell
AI_DASHSCOPE_API_KEY=your-real-key
```

The application container will connect to PostgreSQL through the internal hostname `db`.

Legacy compatibility entrypoints remain available:

```powershell
.\start.ps1
start.cmd
```

## What Phase 1 supports

- upload a local paper
- parse and chunk it
- index it into PostgreSQL + pgvector
- ask a grounded question
- receive an answer with citations
- keep session-level L1 working memory
- stream answers over SSE

## Current verification note

The non-Docker Maven test set already passes locally. The Docker startup path should be verified with `docker compose up --build -d`, `docker compose ps`, and a health probe against `http://localhost:8080/actuator/health`.
