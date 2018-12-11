#!/bin/bash

ARTIFACTS=/home/kacper/artifacts
SERVER=/home/kacper/server

cd $ARTIFACTS
rm app.yml mariadb.yml Dockerfile entrypoint.sh
cp $SERVER/src/main/docker/{app.yml,mariadb.yml,Dockerfile,entrypoint.sh} .

cd $SERVER
git fetch
git reset origin/master --hard
mvn clean
sh add_client.sh
mvn package -Pprod -DskipTests
cp $SERVER/target/carcare-*.war $ARTIFACTS/.

docker build -t carcare:latest $ARTIFACTS
docker-compose -f $ARTIFACTS/app.yml down
docker-compose -f $ARTIFACTS/app.yml up -d
