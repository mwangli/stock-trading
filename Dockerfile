FROM openjdk:8-jdk-slim
LABEL maintainer=mwangli

COPY target/*.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]