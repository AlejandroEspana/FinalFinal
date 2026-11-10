# =========================================================================
# Etapa 1: Compilación / Builder (Maven + OpenJDK 17 Alpine)
# =========================================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# =========================================================================
# Etapa 2: Ejecución Ligera / Runtime (Temurin JRE 17 Alpine)
# =========================================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER appuser

EXPOSE 8088

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
