FROM gradle:8.14.5-jdk17 AS build
WORKDIR /source
COPY . .
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --gid 10001 platform && useradd --uid 10001 --gid platform platform \
    && mkdir -p /app/workspaces && chown -R platform:platform /app
COPY --from=build /source/build/libs/agentic-engineering-platform-0.1.0.jar /app/platform.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/platform.jar"]
