# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached)
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Create execution container
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/leetcode-mentor-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 9094

ENTRYPOINT ["java", "-jar", "app.jar"]
