#!/bin/bash

ARTIFACTS=/home/kacper/carcare/artifacts
SERVER=/home/kacper/carcare/server
MISC=/home/kacper/carcare/misc

cd $ARTIFACTS
rm app.yml mariadb.yml Dockerfile entrypoint.sh *.war
cp $SERVER/src/main/docker/{app.yml,mariadb.yml,Dockerfile,entrypoint.sh} .
ln -sf $MISC/env $ARTIFACTS/env
sed -i 's/${MARIADB_PASSWORD_ENV}/'$(grep MARIADB_PASSWORD_ENV= $ARTIFACTS/env | cut -d '=' -f2-)'/g' $ARTIFACTS/app.yml
sed -i 's/${MARIADB_PASSWORD_ENV}/'$(grep MARIADB_PASSWORD_ENV= $ARTIFACTS/env | cut -d '=' -f2-)'/g' $ARTIFACTS/mariadb.yml
sed -i 's/${MAIL_PASSWORD_ENV}/'$(grep MAIL_PASSWORD_ENV= $ARTIFACTS/env | cut -d '=' -f2-)'/g' $ARTIFACTS/app.yml

cd $SERVER
git fetch
git reset origin/master --hard
mvn clean
sh add_client.sh
mvn package -Pprod -DskipTests
cp $SERVER/target/carcare-*.war $ARTIFACTS/.

docker build -t carcare:latest $ARTIFACTS
docker-compose -f $ARTIFACTS/app.yml down
docker rmi $(docker images --filter "dangling=true" -q --no-trunc)
docker-compose -f $ARTIFACTS/app.yml up -d
