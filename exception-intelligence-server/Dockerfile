FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

ARG JAR_FILE=target/exception-intelligence-server-*.jar
COPY ${JAR_FILE} app.jar

ENV SERVER_PORT=8090
EXPOSE ${SERVER_PORT}

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
