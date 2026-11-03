FROM node:20-bookworm-slim AS ui-build
WORKDIR /workspace/app/ui
COPY app/ui/package*.json ./
RUN npm install
COPY app/ui/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS server-build
WORKDIR /workspace
RUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*
COPY app/pom.xml app/pom.xml
COPY app/engine/ app/engine/
COPY app/server/pom.xml app/server/pom.xml
COPY app/server/src app/server/src
COPY --from=ui-build /workspace/app/ui/dist app/server/src/main/resources/static
RUN mvn -B -f app/pom.xml -DskipTests -pl server -am package

FROM eclipse-temurin:25-jre
WORKDIR /app
ARG CAPYBEE_VERSION=0.1.0-SNAPSHOT
ARG CAPYBEE_REVISION=unknown
LABEL org.opencontainers.image.title="CapyBee server" \
      org.opencontainers.image.version="$CAPYBEE_VERSION" \
      org.opencontainers.image.revision="$CAPYBEE_REVISION"
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Xss256k -Dfile.encoding=UTF-8"
COPY --from=server-build /workspace/app/server/target/capybee-server-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
