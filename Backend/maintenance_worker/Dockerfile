# ==============================================================================
# ETAPA 1: Builder (Compilación con Maven y JDK 17 optimizando caché de capas)
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# ETAPA 2: Runtime (Imagen ligera JRE Alpine con principio de menor privilegio)
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser:appgroup

EXPOSE 8086

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
