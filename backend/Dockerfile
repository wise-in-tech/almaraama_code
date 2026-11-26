# syntax=docker/dockerfile:1.7
# Build image of the platform (ADR-002, ADR-023). Tests run in CI before this build, with Testcontainers.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
# Builds the deployable app module and the modules it assembles; the Maven cache survives between builds.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl platform/app -am package -DskipTests \
 && java -Djarmode=tools -jar platform/app/target/almaraama.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-noble
# Non-root user, read-only application files.
RUN groupadd --system --gid 10001 almaraama \
 && useradd --system --uid 10001 --gid almaraama --no-create-home --shell /usr/sbin/nologin almaraama \
 && mkdir -p /var/lib/almaraama/files && chown 10001:10001 /var/lib/almaraama/files
# /var/lib/almaraama/files: stored files (ADR-038), a volume in compose; the only writable path besides /tmp.
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER 10001:10001
EXPOSE 8080 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
