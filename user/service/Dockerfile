FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -q -pl user/service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/user/service/target/user-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 9093
ENTRYPOINT ["java", "-jar", "app.jar"]
