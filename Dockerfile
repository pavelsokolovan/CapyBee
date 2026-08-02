FROM node:20-bookworm-slim AS ui-build
WORKDIR /workspace/app/ui
COPY app/ui/package*.json ./
RUN npm install
COPY app/ui/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS server-build
WORKDIR /workspace
COPY app/server/pom.xml app/server/pom.xml
COPY app/server/src app/server/src
COPY --from=ui-build /workspace/app/ui/dist app/server/src/main/resources/static
WORKDIR /workspace/app/server
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Xss256k -Dfile.encoding=UTF-8"
COPY --from=server-build /workspace/app/server/target/capybee-server-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
