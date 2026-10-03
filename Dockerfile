# Imagem única (SPA + API). Build: docker build -t escalas-coelho .

# 1) Frontend: o build do Vite é gravado em backend/src/main/resources/static.
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) Backend: empacota o .jar já com a SPA embutida (os testes rodam no pipeline de CI).
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -B -q -DskipTests package && cp target/escalas-*.jar /app/app.jar

# 3) Execução: apenas JRE, usuário sem privilégios.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=backend /app/app.jar app.jar
USER app
EXPOSE 8080 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
