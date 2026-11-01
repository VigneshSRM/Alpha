# --- build -------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# The shared CSS/JS/images live in the Python app, one level up.
COPY ../app/static /app/static
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests -Dshared.static.dir=/app/static

# --- run ---------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S alpha && adduser -S alpha -G alpha
COPY --from=build /workspace/target/alpha-portal-*.jar app.jar
USER alpha

ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
