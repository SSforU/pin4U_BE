# Dockerfile — 표준 빌드
FROM --platform=linux/amd64 eclipse-temurin:17-jre-jammy

ARG JAR_FILE=build/libs/pin4U_BE-0.0.1-SNAPSHOT.jar
WORKDIR /app
COPY ${JAR_FILE} /app/app.jar
COPY ${JAR_FILE} /app.jar


EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

