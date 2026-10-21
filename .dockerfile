# ==========================================
# ETAPA 1: Compilación de la aplicación
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ==========================================
# ETAPA 2: Imagen liviana de ejecución (JRE)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /app/target/ControlProyectosAPI-*.jar app.jar
USER spring:spring

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]