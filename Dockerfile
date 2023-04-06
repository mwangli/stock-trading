FROM openjdk:8-jdk-slim
LABEL maintainer=mwangli

COPY app.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]