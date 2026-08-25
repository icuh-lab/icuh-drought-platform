# 첫번째 스테이지 시작
FROM eclipse-temurin:17-jdk AS builder

# 컨테이너의 작업 경로 설정
WORKDIR /workspace/app

# 호스트에서 빌드된 파일을 컨테이너로 복사
COPY ./gradlew /workspace/app/
COPY ./settings.gradle /workspace/app/
COPY ./build.gradle /workspace/app/
COPY ./gradle /workspace/app/gradle/
COPY ./src /workspace/app/src/

# 빌드 실행
RUN /workspace/app/gradlew build -x test
RUN mv /workspace/app/build/libs/icuh-platform-admin-0.0.1-SNAPSHOT.jar /workspace/app/icuh-platform-admin.jar


# 두번째 스테이지 시작
FROM eclipse-temurin:17-jre AS runner

# 각 스테이지는 별도의 환경이기 떄문에 다시 선언
WORKDIR /workspace/app

# 이전 스테이지에서 jar 파일 복사
COPY --from=builder /workspace/app/icuh-platform-admin.jar /workspace/app/icuh-platform-admin.jar

# 컨테이너 실행시에 app.jar 실행
ENTRYPOINT ["java", "-jar", "icuh-platform-admin.jar"]