#!/bin/bash

ARTIFACTS=/home/kacper/carcare/artifacts
SERVER=/home/kacper/carcare/server
CLIENT=/home/kacper/carcare/client
MISC=/home/kacper/carcare/misc

ln -sf $MISC/env $ARTIFACTS/env
sed -i 's/${MARIADB_PASSWORD_ENV}/'$(grep MARIADB_PASSWORD_ENV= $ARTIFACTS/env | cut -d '=' -f2-)'/g' $ARTIFACTS/app.yml
sed -i 's/${MARIADB_PASSWORD_ENV}/'$(grep MARIADB_PASSWORD_ENV= $ARTIFACTS/env | cut -d '=' -f2-)'/g' $ARTIFACTS/mariadb.yml
sed -i 's/${MAIL_PASSWORD_ENV}/'$(grep MAIL_PASSWORD_ENV= $ARTIFACTS/env | cut -d '=' -f2-)'/g' $ARTIFACTS/app.yml
sed -i 's/${MAIL_BASE_URL_ENV}/'$(grep MAIL_BASE_URL_ENV= $ARTIFACTS/env | cut -d '=' -f2-)'/g' $ARTIFACTS/app.yml

docker-compose -f $ARTIFACTS/app.yml down
docker-compose -f $ARTIFACTS/app.yml up -d
