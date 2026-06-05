# syntax=docker/dockerfile:1
# The line above opts into the modern Dockerfile parser (enables newer features
# and better caching). It must be the very first line to take effect.

# ==============================================================================
# STAGE 1 — "build": compile the code and produce the runnable JAR.
# ==============================================================================
# We start FROM an image that already contains Gradle 8 + a JDK 21 (the full
# Java DEVELOPMENT kit, with the compiler javac). "AS build" names this stage so
# the second stage can copy files out of it.
FROM gradle:8-jdk21 AS build

# All following commands run inside this directory in the image's filesystem.
WORKDIR /app

# --- Dependency layer (for caching) ---        
# Copy ONLY the build scripts first. Docker caches each instruction as a layer;
# as long as these files don't change, Docker reuses the cached "downloaded
# dependencies" layer on the next build instead of re-downloading everything.
COPY build.gradle settings.gradle ./

# Pre-download dependencies. "|| true" lets this step succeed even though there's
# no source code yet to build — we just want the dependency cache populated.
# --no-daemon: in a container there's no long-lived Gradle process to reuse, so
# the background daemon only wastes memory.
RUN gradle dependencies --no-daemon || true

# --- Source layer ---
# NOW copy the actual source. If you only change a .java file, the cached
# dependency layer above is still valid and the rebuild is much faster.
COPY src ./src

# Build the executable "fat JAR" (the app + all its libraries in one file).
# -x test skips tests during the image build — tests belong in CI, and they'd
# need their own DB setup here. Remove "-x test" if you want them to run.
RUN gradle bootJar --no-daemon -x test

# ==============================================================================
# STAGE 2 — "runtime": a tiny image that only RUNS the JAR.
# ==============================================================================
# eclipse-temurin:21-jre-alpine = just a Java RUNTIME (JRE, no compiler) on top
# of Alpine Linux (a minimal ~5 MB distro). No Gradle, no JDK, no source code.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# --- Run as a non-root user (security best practice) ---
# By default containers run as root; if the app is ever exploited, root inside
# the container is a bigger blast radius. We create an unprivileged user instead.
# -S = system account, -G = add user to the group.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Copy ONLY the finished JAR out of the build stage. None of Stage 1's tooling
# or intermediate files come along — that's the whole point of multi-stage.
# *.jar avoids hardcoding the version number in the filename.
COPY --from=build /app/build/libs/*.jar app.jar

# Documents that the app listens on 8080. This is informational; the actual
# port publishing happens in docker-compose.yml ("ports:").
EXPOSE 8080

# The command that runs when the container starts: launch the Spring Boot app.
# Exec form (JSON array) so Java becomes PID 1 and receives stop signals
# (Ctrl-C / docker stop) directly, allowing a clean shutdown.
ENTRYPOINT ["java", "-jar", "app.jar"]
