# Stage 1: build. This image has Maven and a full JDK - everything needed
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests
# Stage 2: run. A JRE (no compiler, no Maven, no build tools) 
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/team-skeleton.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
