FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/target/research-assistant-phase1-0.0.1-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /app/storage

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
