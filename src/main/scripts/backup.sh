#!/bin/bash

DB_PASS='pass'
BACKUP_DIR=~/carcare/misc/backup
DATA_DIR=~/carcare/data
DB_BACKUP_FILE=db_backup.sql
NOW=$(date +"%Y-%m-%d")

docker exec carcare-mariadb /usr/bin/mysqldump -u root carcare -p$DB_PASS > $BACKUP_DIR/$DB_BACKUP_FILE
tar czf $BACKUP_DIR/backup_$NOW.tar.gz -C $DATA_DIR data -C $BACKUP_DIR $DB_BACKUP_FILE
rm $BACKUP_DIR/$DB_BACKUP_FILE
