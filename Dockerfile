FROM java11-alpine

LABEL maintainer=mwangli

COPY target/*.jar app.jar

ENTRYPOINT ["java","-Djava.awt.headless=true", "-jar","app.jar"]