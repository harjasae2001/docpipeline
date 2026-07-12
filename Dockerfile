# Stage 1: Build using the official Maven image
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy only the POM first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B 2>/dev/null || true

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime (Unchanged)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]