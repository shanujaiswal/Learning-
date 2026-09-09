# Docker (Conceptual Overview)

--> Docker packages an application together with everything it needs to run (code, runtime, system libraries, dependencies) into a single unit called a **container** -- so it runs identically on any machine, regardless of what's installed on the host.
--> Solves the classic "it works on my machine" problem -- since the container carries its own environment, it behaves the same on a developer's laptop, a teammate's machine, or a production server.

# Image vs Container

--> Image --> A read-only template/blueprint containing the application code + all its dependencies + OS-level libraries -- think of it like a class.
--> Container --> A running instance of an image -- think of it like an object created from that class. You can run multiple containers from the same image.

# Dockerfile

--> A Dockerfile is a text file with step-by-step instructions for building an image -- e.g. which base OS/runtime to start from, which files to copy in, which dependencies to install, and what command to run when the container starts.
```dockerfile
FROM node:20-alpine        # Base image to start from
WORKDIR /app                # Sets the working directory inside the container
COPY package*.json ./       # Copies dependency files first (for build caching)
RUN npm install              # Installs dependencies
COPY . .                     # Copies the rest of the application code
EXPOSE 3000                  # Documents which port the app listens on
CMD ["npm", "start"]         # Command to run when the container starts
```

# Containers vs Virtual Machines

--> A VM virtualizes an entire operating system (its own kernel, full OS) -- heavier, slower to start (minutes), more isolated.
--> A container shares the host machine's OS kernel and only packages the application layer on top -- much lighter, starts in seconds, and many containers can run efficiently on one machine.

# Common Docker Commands

--> `docker build -t myapp .` --> Builds an image from a Dockerfile in the current directory, tagging it "myapp".
--> `docker run myapp` --> Runs a container from an image.
--> `docker run -p 3000:3000 myapp` --> Runs a container, mapping port 3000 on the host to port 3000 inside the container.
--> `docker ps` --> Lists currently running containers.
--> `docker stop <container_id>` --> Stops a running container.
--> `docker images` --> Lists locally available images.
--> `docker-compose up` --> Starts multiple related containers together (e.g. an app + its database) as defined in a `docker-compose.yml` file, instead of running each `docker run` command manually.

# Why Docker Matters for Full-Stack Development

--> Ensures every developer on a team runs the exact same environment (same Node/Python version, same OS-level dependencies) without manual setup instructions.
--> Makes deployment consistent -- the same container tested locally is the one that runs in production, eliminating environment-drift bugs.
--> Simplifies running supporting services locally (e.g. spinning up a PostgreSQL database in a container instead of installing it directly on your machine).
