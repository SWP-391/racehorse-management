# One image for the whole system (TECH-106): the web client is built, copied into the service jar, and served
# by Spring Boot from the same origin as the API. Built by Railway from the repository root, or locally:
#   docker build -t racehorse .
#
# Stage 1: web client
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# Stage 1b: render docs/SRS.md to static HTML (TECH-191), so /docs ships pre-rendered content
FROM node:22-alpine AS docs
WORKDIR /docs-build
RUN npm install --no-audit --no-fund --no-save marked@12.0.2
COPY scripts/render-srs.mjs ./render-srs.mjs
COPY docs/SRS.md docs/STATE-MACHINES.md ./
RUN node render-srs.mjs SRS.md srs.html && node render-srs.mjs STATE-MACHINES.md state-machines.html

# Stage 2: service jar with the web client inside (dependencies in their own cached layer)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
COPY docs/ ./src/main/resources/static/docs/
COPY --from=docs /docs-build/srs.html /docs-build/state-machines.html ./src/main/resources/static/docs/
RUN mvn -B -q package -DskipTests && cp target/racehorse-backend-*.jar /build/app.jar

# Stage 3: runtime, non-root
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /app /data/videos/photos \
 && chown -R app:app /app /data
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar

# Production defaults (TECH-110). Every one can be overridden by a platform variable.
ENV SESSION_COOKIE_SECURE=true \
    FORWARD_HEADERS_STRATEGY=native \
    SPRINGDOC_ENABLED=false \
    SERVER_COMPRESSION_ENABLED=true \
    VIDEO_DIR=/data/videos \
    PHOTO_DIR=/data/videos/photos \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
