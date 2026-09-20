FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend_parcial/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY backend_parcial/src src
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
