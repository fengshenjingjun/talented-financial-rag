FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY build/libs/financial-rag.jar app.jar

EXPOSE 8088

ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
