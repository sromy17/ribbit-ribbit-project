# Stage 1: build. This image has Maven and a full JDK - everything needed
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
COPY olap.sql .
RUN mvn -B clean package -DskipTests
# Stage 2: run. A JRE (no compiler, no Maven, no build tools) 
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
RUN mkdir -p /app/state && chown app:app /app/state
COPY --from=build /build/target/team-skeleton.jar app.jar
USER app
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
