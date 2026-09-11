FROM gradle:8.14.5-jdk17
# Trusted context: cache/modules-2 only; exclude lock/gc files and credentials.
COPY --chown=gradle:gradle cache /opt/gradle-readonly
ENV GRADLE_RO_DEP_CACHE=/opt/gradle-readonly
