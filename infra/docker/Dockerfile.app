# 공용 앱 이미지. 빌드는 호스트에서 수행하고 JAR만 담는다 (NAS 2코어 배려)
FROM eclipse-temurin:21-jre

ARG JAR_FILE
COPY ${JAR_FILE} /app/app.jar

ENV JAVA_OPTS="-Xms128m -Xmx384m"
ENV TZ=Asia/Seoul

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
