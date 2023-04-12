set HOST=root@test
set APP=found-trading
scp -r Dockerfile %HOST%:build
scp -r target/*.jar %HOST%:build/app.jar
ssh %HOST% build/build.sh %APP%