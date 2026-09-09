# Why Docker Compose

--> A real app usually isn't one container -- it's several (e.g. a frontend, a backend API, a database, maybe Redis). Running each with separate `docker run` commands (remembering flags, networks, volumes every time) doesn't scale.
--> Docker Compose defines a whole multi-container application declaratively in a single `docker-compose.yml` file -- one command starts/stops the entire stack together.

# Anatomy of a docker-compose.yml

```yaml
version: "3.9"

services:
  api:
    build: ./api                # Build from a Dockerfile in ./api
    ports:
      - "3000:3000"
    environment:
      - DB_HOST=db
      - DB_PORT=5432
    depends_on:
      - db
    volumes:
      - ./api:/app               # Bind mount for live code reload in dev

  db:
    image: postgres:16           # Pull an existing image instead of building
    environment:
      - POSTGRES_PASSWORD=secret
    volumes:
      - db-data:/var/lib/postgresql/data   # Named volume for persistence
    ports:
      - "5432:5432"

volumes:
  db-data:                       # Declares the named volume used above
```

--> `services` -- each entry is one container. `build` compiles from a local Dockerfile; `image` pulls a pre-built one from a registry.
--> `depends_on` -- controls START ORDER only (db starts before api) -- it does NOT wait for the database to be actually ready to accept connections; apps should still retry their DB connection on startup.
--> All services on the same Compose file automatically share a network -- `api` can reach `db` simply by using `db` as the hostname (no manual `docker network create` needed, unlike raw `docker run`).

# Common Compose Commands

```bash
docker-compose up               # Build (if needed) and start all services, attached to logs
docker-compose up -d            # Same, but detached (runs in the background)
docker-compose down             # Stop and remove all containers (add -v to also remove volumes)
docker-compose logs -f api      # Follow logs for just the "api" service
docker-compose exec api sh      # Open a shell inside the running "api" container
docker-compose build            # Rebuild images without starting containers
```

# Environment-Specific Overrides

--> `docker-compose.override.yml` -- Compose automatically merges this on top of the base file, commonly used to add dev-only settings (bind mounts, debug ports) without touching the base config used in production.
--> A separate `docker-compose.prod.yml` explicitly loaded with `-f` is another common pattern for keeping dev/prod configuration cleanly separated.

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

# When Compose Is Enough (vs Needing Kubernetes)

--> Compose is ideal for local development and small, single-server deployments -- it doesn't handle multi-server clustering, automatic failover, or rolling zero-downtime updates across machines.
--> Many teams use Compose for local dev even when Kubernetes runs the same containers in production -- the Dockerfiles/images stay identical, only the orchestration layer changes.
