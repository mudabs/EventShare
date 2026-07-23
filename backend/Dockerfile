# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM maven:3.9-amazoncorretto-25 AS build
WORKDIR /app
# Cache dependencies first for faster incremental builds
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app
# curl: container healthcheck. ffmpeg/ffprobe: in-process video poster + duration.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 appuser
COPY --from=build /app/target/eventshare-api-0.1.0.jar app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
