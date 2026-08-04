# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy and build jwt-library first
COPY jwt-library ./jwt-library
RUN cd jwt-library && mvn clean install -DskipTests

# Copy only the files needed to download dependencies
COPY auth-service/pom.xml .
# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY auth-service/src ./src

# Build the application
RUN mvn package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Set the timezone
RUN apk add --no-cache tzdata
ENV TZ=Asia/Kolkata

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
