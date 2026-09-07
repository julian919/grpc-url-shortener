# Multi-stage: stage 1 needs the WHOLE reactor (shortener/service depends on
# shortener/api and linkaudit/api), so the build context is the repo root, not
# this directory -- see docker-compose.yml's `context: .` for this service.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -q -pl shortener/service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/shortener/service/target/shortener-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
