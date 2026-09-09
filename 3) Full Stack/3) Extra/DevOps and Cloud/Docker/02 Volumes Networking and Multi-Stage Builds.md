# The Problem -- Containers Are Ephemeral

--> By default, any data written inside a container's filesystem disappears when the container is removed -- fine for stateless app code, but a problem for databases, uploaded files, or anything that needs to persist.
--> Docker solves this with **Volumes** (for persistence) and connects containers to each other/the outside world with **Networks**.

# Volumes -- Persisting Data

--> Named Volume -- Docker manages the storage location on the host; the easiest and most portable way to persist data (e.g. a database's files).
--> Bind Mount -- maps a specific folder on the host machine directly into the container; commonly used in development so code edits on the host are reflected instantly inside the running container.
--> `tmpfs` mount -- stored only in host memory, never written to disk; used for temporary sensitive data.

```bash
docker volume create db-data                              # Create a named volume
docker run -v db-data:/var/lib/postgresql/data postgres    # Mount it into a container

docker run -v $(pwd):/app -p 3000:3000 my-app              # Bind mount current folder for live-reload dev
```

--> Without a volume, `docker rm` on a database container silently deletes all its data -- always mount a volume for anything stateful before running in anything beyond a quick throwaway test.

# Networking -- Letting Containers Talk to Each Other

--> By default, Docker creates an isolated network per project (or per Compose file) -- containers on the same network can reach each other by their **container/service name** as a hostname, without knowing IP addresses.
--> `docker network create mynet` -- creates a custom network; `docker run --network mynet ...` attaches a container to it.
--> Port mapping (`-p host:container`) only controls access from OUTSIDE Docker (the host machine) -- containers on the same network reach each other directly over the container's internal port, mapping not required.

```bash
docker network create mynet
docker run --network mynet --name db postgres
docker run --network mynet --name api -e DB_HOST=db my-api   # "db" resolves via Docker's internal DNS
```

# Multi-Stage Builds -- Smaller, Cleaner Production Images

--> Problem -- a naive Dockerfile often bundles build tools (compilers, dev dependencies, source maps) into the final image, making it needlessly large and exposing more attack surface.
--> Multi-stage builds use multiple `FROM` statements in one Dockerfile -- each stage can use a different base image, and only the final stage's output ships in the resulting image.

```dockerfile
# Stage 1: build the app (has full Node.js + dev dependencies)
FROM node:20 AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# Stage 2: serve only the built output (tiny final image, no build tools)
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

--> Result -- the final image only contains Nginx + the compiled static files, not Node.js, npm packages, or source code -- often shrinking image size from 1GB+ down to tens of MB.

# .dockerignore -- Keeping Build Context Clean

--> Like `.gitignore`, but for `docker build` -- excludes files/folders from being sent to the Docker daemon during build (faster builds, smaller images, avoids accidentally baking in secrets like `.env` or `node_modules`).

```
node_modules
.env
.git
*.log
```

# Build Caching Best Practices

--> Docker caches each Dockerfile instruction's layer -- order instructions from LEAST to MOST frequently changing, so cache hits are maximized on rebuilds.
--> Classic pattern: copy `package*.json` and run `npm install` BEFORE copying the rest of the source code -- dependency installation is cached and skipped unless `package.json` itself changes, even if application code changes constantly.
