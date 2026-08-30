#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# HISTORICAL — NOT DEPLOYED, AND DESTRUCTIVE IF RUN.
#
# The live deployment is Compose project `services`, config file
# /home/kacper/services/carcare.yml, in a separate private git repository.
# Nothing in THIS repository invokes this script — no CI job, no pom plugin
# (verified 2026-08-30). See AGENTS.md § Deployment for the verified topology.
#
# DO NOT RUN THIS. The `sed -i` lines below rewrite the ${..._ENV} placeholders
# in app.yml / mariadb.yml IN PLACE — one run consumes the templates permanently
# and leaves no way to regenerate them short of `git checkout`. It then brings
# up a `carcare-app` container that is not part of the running deployment.
# ─────────────────────────────────────────────────────────────────────────────

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
