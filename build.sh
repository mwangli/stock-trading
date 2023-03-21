#!/bin/bash

APP=$1
REGISTER=registry-vpc.cn-shenzhen.aliyuncs.com
NS=mwangli

cd build
docker build -t $APP -f Dockerfile .
docker login --username=limingwang06 --password=Dknhfre1st $REGISTER
docker tag $APP $REGISTER/$NS/$APP
docker push $REGISTER/$NS/$APP

# kubectl set image sts/$APP $APP=$REGISTER/$NS/$APP --record
kubectl delete pod ${APP}-0
