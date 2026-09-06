FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY apps/api/pom.xml apps/api/pom.xml
COPY apps/api/.mvn apps/api/.mvn
COPY apps/api/mvnw apps/api/mvnw
RUN chmod +x apps/api/mvnw
COPY apps/api/src apps/api/src
RUN ./apps/api/mvnw -B -f apps/api/pom.xml -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy

ARG HAOBLOG_REVISION=unknown
ARG HAOBLOG_BUILD_INPUT=unknown
ARG HAOBLOG_CONFIG_VERSION=s6-v3
LABEL io.haoblog.revision=$HAOBLOG_REVISION \
      io.haoblog.build-input=$HAOBLOG_BUILD_INPUT \
      io.haoblog.config-version=$HAOBLOG_CONFIG_VERSION \
      io.haoblog.flyway-version=17

RUN groupadd --system haoblog \
    && useradd --system --gid haoblog --home-dir /nonexistent --shell /usr/sbin/nologin haoblog

WORKDIR /app
COPY --from=build /workspace/apps/api/target/haoblog-api-*.jar /app/app.jar
RUN chown -R haoblog:haoblog /app
USER haoblog

ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=6 CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
