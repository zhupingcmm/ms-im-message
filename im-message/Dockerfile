# syntax=docker/dockerfile:1.7
# Multi-stage build for im-message
# Build:  docker build --build-arg SERVICE=im-message -t im-message:local -f im-message/Dockerfile .
ARG SERVICE=im-message

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

EXPOSE 8082
USER nonroot
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "/app/app.jar"]
