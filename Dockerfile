# ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache maven

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S ebooks && adduser -S ebooks -G ebooks

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

RUN chown ebooks:ebooks app.jar

USER ebooks

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
