# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
# Java 21 application을 고정 base image의 비루트 runtime image로 만들며 발행과 배포는 외부 workflow가 담당합니다.

# builder와 runtime base를 digest로 고정해 registry tag 변경이 build 입력을 바꾸지 못하게 합니다.
ARG BUILD_IMAGE=eclipse-temurin:21-jdk-jammy@sha256:9d8dcf999b0bce2453e913823595a5ff2a4e8e9e5d5241b45280d0ff069818ec
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy@sha256:d63bd8d9b171999cbed8576f2c76e874dd4856791a358536e5c4d407e77edc13

# builder stage는 Gradle Wrapper로 application JAR를 생성하며 Gradle·JDK와 source tree를 runtime stage로 복사하지 않습니다.
FROM ${BUILD_IMAGE} AS builder

WORKDIR /workspace

COPY --chmod=0755 gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./

# source를 복사하기 전에 dependency를 받아 BuildKit의 Gradle cache를 source 변경 뒤에도 재사용합니다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon --console=plain --quiet

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon --console=plain && \
    cp build/libs/*.jar /workspace/application.jar

# runtime stage는 JRE base에 고정 UID/GID를 만들고 생성한 JAR를 실행할 production process를 정의합니다.
FROM ${RUNTIME_IMAGE} AS runtime

ARG SOURCE_URL=https://github.com/ckn1070/community
ARG REVISION
ARG CREATED
ARG SOURCE_DIRTY=false

LABEL org.opencontainers.image.source="${SOURCE_URL}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.created="${CREATED}" \
      io.carmunity.source-dirty="${SOURCE_DIRTY}"

# OCI revision은 소문자 40자리 Git SHA만 허용하고 created·dirty metadata 입력도 검증합니다.
RUN case "${REVISION}" in \
      ''|*[!0-9a-f]*) echo "REVISION은 소문자 40자리 Git SHA여야 합니다." >&2; exit 1 ;; \
    esac && \
    test "${#REVISION}" -eq 40 && \
    test -n "${CREATED}" && \
    case "${SOURCE_DIRTY}" in true|false) ;; *) echo "SOURCE_DIRTY는 true 또는 false여야 합니다." >&2; exit 1 ;; esac && \
    groupadd --system --gid 10001 carmunity && \
    useradd --system --uid 10001 --gid 10001 --home-dir /nonexistent --shell /usr/sbin/nologin carmunity

WORKDIR /app

COPY --from=builder --chown=10001:10001 /workspace/application.jar /app/application.jar

# Java heap 상한은 container memory limit의 75%로 계산하고 Java 임시 파일 경로는 /tmp를 사용합니다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/tmp"

EXPOSE 9097

# 고정 UID/GID로 실행해 root 권한을 제거하고 배포 환경마다 파일 소유자가 달라지지 않게 합니다.
USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
