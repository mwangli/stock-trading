set HOST=root@test
set APP=found-trading
scp Dockerfile %HOST%:build
scp target/*.jar %HOST%:build/app.jar
ssh  %HOST% build/build.sh %APP%
