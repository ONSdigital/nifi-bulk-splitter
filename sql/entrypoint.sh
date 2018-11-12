#!/usr/bin/env bash

sh -c "
echo 'starting setup'
sleep 20
/usr/config/configure-db.sh
echo 'database setup finished'
" & exec /opt/mssql/bin/sqlservr