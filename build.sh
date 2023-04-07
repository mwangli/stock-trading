#!/bin/bash

APP=$1
REGISTER=registry-vpc.cn-shenzhen.aliyuncs.com
NS=mwangli
USER=limingwang06
PASS=Dknhfre1st

cd build
docker build -t $APP -f Dockerfile .
docker login --username=$USER --password=$PASS $REGISTER
docker tag $APP $REGISTER/$NS/$APP
docker push $REGISTER/$NS/$APP

#kubectl set image sts/$APP $APP=$REGISTER/$NS/$APP --record
kubectl delete pod ${APP}-0
