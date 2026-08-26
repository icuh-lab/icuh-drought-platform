# 3개 실행 모듈이 공유한다. jar는 CI에서 ./gradlew build로 만든 것을 복사만 한다.
# 로컬에서 쓰려면 docker build 전에 ./gradlew build 가 선행되어야 한다.
FROM eclipse-temurin:17-jre

ARG MODULE
WORKDIR /app

# 이 파이프라인이 만든 이미지임을 표시한다. EC2의 배포 후 정리(deploy.yml의
# docker image prune)가 이 라벨로 대상을 좁힌다 — 라벨이 없으면 나이만 보고 지우게 되어
# 런북이 백업해 둔 icuh-platform:rollback(구 앱 복구 수단)까지 함께 지워진다.
LABEL re.kr.icuh.project=drought-platform

COPY ${MODULE}/build/libs/*-SNAPSHOT.jar app.jar

# 힙 상한은 "컨테이너 메모리 한도의 70%"다. 한도가 걸려 있지 않으면 그 기준이 호스트 전체
# 메모리가 되어, EC2 한 대에 JVM 3개가 뜨는 이 구성에서는 210% 과다배정이 된다.
# 한도는 이 파일이 아니라 deploy/docker-compose.yml의 서비스별 mem_limit이 건다 —
# 그 값을 지우면 이 옵션은 보호 장치가 아니라 위험 요소가 된다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
