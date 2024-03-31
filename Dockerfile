FROM openjdk:11-jdk-slim

LABEL maintainer=mwangli

COPY target/*.jar app.jar

ENTRYPOINT ["java","-Djava.awt.headless=true", "-jar","app.jar"]