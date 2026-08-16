# syntax=docker/dockerfile:1
# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# jwt-library is no longer local — it's pulled from GitHub Packages
# (published by the raitukashtam platform repo's CI). Auth is passed in
# as a BuildKit secret so the token never lands in an image layer.
COPY settings.xml /root/.m2/settings.xml

# Copy only the files needed to download dependencies
COPY pom.xml .
RUN --mount=type=secret,id=github_token \
    GITHUB_ACTOR=harikrish438 GITHUB_TOKEN=$(cat /run/secrets/github_token) \
    mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN --mount=type=secret,id=github_token \
    GITHUB_ACTOR=harikrish438 GITHUB_TOKEN=$(cat /run/secrets/github_token) \
    mvn package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port the app runs on
EXPOSE 8081

# Set the timezone
RUN apk add --no-cache tzdata
ENV TZ=Asia/Kolkata

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
