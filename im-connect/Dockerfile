# syntax=docker/dockerfile:1.7
# Multi-stage build for im-connect (Netty WebSocket 长连接网关)
# Build:  docker build --build-arg SERVICE=im-connect -t im-connect:local -f im-connect/Dockerfile .
ARG SERVICE=im-connect

FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY im-common im-common
COPY im-user im-user
COPY im-message im-message
COPY im-connect im-connect
COPY im-gateway im-gateway
COPY im-media im-media
COPY im-push im-push

RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && \
    ./mvnw -B -q -DskipTests -pl ${SERVICE} -am package

RUN cp ${SERVICE}/target/${SERVICE}-*.jar /app.jar

FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app
COPY --from=build /app.jar /app/app.jar

# Spring Boot HTTP 端口（actuator 用）+ Netty WebSocket 端口
EXPOSE 8083 8091
USER nonroot
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "/app/app.jar"]
