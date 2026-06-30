FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

ARG JAR_FILE=build/libs/JWT-0.0.1-SNAPSHOT.jar
COPY --chown=10001:10001 ${JAR_FILE} /app/app.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
