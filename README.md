# Research Assistant Phase 1

## One-command start

After Docker Desktop and Maven are available, the easiest local startup path is:

```powershell
.\start.ps1
```

You can also use:

```cmd
start.cmd
```

The startup script will:

1. start PostgreSQL + pgvector with `docker compose up -d db`
2. wait until the database is actually healthy
3. check that `AI_DASHSCOPE_API_KEY` is available from your environment or `.env`
4. run the Spring Boot app with Maven and the workspace-local repository at `.m2/repository`

## Manual startup

### 1. Start the database

```powershell
docker compose up -d db
```

### 2. Provide environment variables

```powershell
$env:AI_DASHSCOPE_API_KEY="your-real-key"
```

You can copy `.env.example` to `.env` and fill the values instead.

### 3. Run the app

```powershell
mvn spring-boot:run
```

If Maven is not on your `PATH`, the script will automatically fall back to `D:\apache-maven-3.9.11\bin\mvn.cmd`.

### 4. Open the UI

Visit [http://localhost:8080](http://localhost:8080).

## What Phase 1 supports

- upload a local paper
- parse and chunk it
- index it into PostgreSQL + pgvector
- ask a grounded question
- receive an answer with citations
- keep session-level L1 working memory
- stream answers over SSE

## Current verification note

The local non-Docker test set passes in the current environment. Docker/Testcontainers-based integration tests are already written, but they cannot run until Docker is installed and available to the current shell.
