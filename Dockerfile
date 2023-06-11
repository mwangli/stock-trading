FROM openjdk:11-jdk-slim

LABEL maintainer=mwangli

# 环境变量(tesseract)
ENV LD_LIBRARY_PATH="/usr/local/lib" \
    LIBLEPT_HEADERSDIR="/usr/local/include" \
    PKG_CONFIG_PATH="/usr/local/lib/pkgconfig"
# 安装tesseract环境
ADD   tesseract-4.1.1.tar.gz /
ADD   leptonica-1.80.0.tar.gz /

RUN   yum -y install file automake libicu-devel libpango1.0-dev libcairo-dev libjpeg-devel libpng-devel libtiff-devel zlib-devel libtool gcc-c++ make \
      && cd /leptonica-1.80.0 && ./configure && make && make install \
      && cd /tesseract-4.1.1 && ./autogen.sh && ./configure && make && make install \
      && rm -rf /leptonica-1.80.0 /tesseract-4.1.1

COPY target/*.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]