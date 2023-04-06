#!/bin/bash

scp -r Dockerfile root@mwang.online:build
scp -r target/*.jar root@mwang.online:build/app.jar
ssh root@mwang.online build/build.sh found-trading