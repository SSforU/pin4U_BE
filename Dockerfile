FROM --platform=linux/amd64 eclipse-temurin:17-jre-jammy

ARG JAR_FILE=build/libs/pin4U_BE-0.0.1-SNAPSHOT.jar
WORKDIR /app
COPY ${JAR_FILE} /app/app.jar

EXPOSE 8080
# 변경됨: sh -c 를 사용하여 환경변수 확장 활성화
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]