FROM node:22-bookworm-slim AS web-build
WORKDIR /workspace
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY Frontend-web/package.json Frontend-web/package.json
RUN corepack enable && pnpm install --frozen-lockfile
COPY Frontend-web Frontend-web
RUN pnpm --filter @modeler/web build

FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY Backend-web/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY Backend-web/src src
COPY --from=web-build /workspace/Frontend-web/dist src/main/resources/static
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
