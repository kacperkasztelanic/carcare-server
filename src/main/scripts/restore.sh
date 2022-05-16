#!/bin/bash

DB_PASS='pass'
BACKUP_DIR=~/carcare/misc/backup
DATA_DIR=~/carcare/data
DB_BACKUP_FILE=db_backup.sql
WORKING_DIR=$BACKUP_DIR/temp

mkdir -p $WORKING_DIR
tar -xf $BACKUP_DIR/$1 -C $WORKING_DIR
cat $WORKING_DIR/$DB_BACKUP_FILE | docker exec -i carcare-mariadb /usr/bin/mysql -u root -p$DB_PASS carcare
rm -rf $DATA_DIR/data
cp -a $WORKING_DIR/data $DATA_DIR
rm -rf $WORKING_DIR
