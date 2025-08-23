FROM eclipse-temurin:17-jre-jammy

# 앱 JAR 복사 (빌드 산출물 경로에 맞춰 유지)
COPY build/libs/*SNAPSHOT.jar /app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
