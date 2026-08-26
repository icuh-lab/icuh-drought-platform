# 3개 실행 모듈이 공유한다. jar는 CI에서 ./gradlew build로 만든 것을 복사만 한다.
# 로컬에서 쓰려면 docker build 전에 ./gradlew build 가 선행되어야 한다.
FROM eclipse-temurin:17-jre

ARG MODULE
WORKDIR /app

COPY ${MODULE}/build/libs/*-SNAPSHOT.jar app.jar

# 컨테이너 메모리 한도를 기준으로 힙을 잡는다. EC2 한 대에 JVM 3개가 뜨므로 여유를 남긴다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
