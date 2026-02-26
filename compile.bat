@echo off
set JAVA_HOME=C:\Users\MS\.jdks\jdk-17.0.12
set PATH=%JAVA_HOME%\bin;%PATH%

cd /d D:\ai-stock-trading

D:\apache-maven-3.6.2\bin\mvn.cmd %*
