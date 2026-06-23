# syntax=docker/dockerfile:1.7
# Multi-stage build for im-user
# Build:  docker build --build-arg SERVICE=im-user -t im-user:local -f im-user/Dockerfile .
# Run:    docker run --rm -p 8081:8081 im-user:local
ARG SERVICE=im-user

FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

# 1) 拷贝构建所需的所有源（让 Maven 解析多模块依赖图）
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY im-common im-common
COPY im-user im-user
COPY im-message im-message
COPY im-connect im-connect
COPY im-gateway im-gateway
COPY im-media im-media
COPY im-push im-push

# 2) 用 BuildKit cache mount 复用 ~/.m2，避免重复下载依赖
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && \
    ./mvnw -B -q -DskipTests -pl ${SERVICE} -am package

# 3) 把打好的 Spring Boot fat-jar 拷出来
RUN cp ${SERVICE}/target/${SERVICE}-*.jar /app.jar

# ---- Runtime stage: distroless 非 root 镜像 ----
FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app
COPY --from=build /app.jar /app/app.jar

EXPOSE 8081
USER nonroot
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "/app/app.jar"]
