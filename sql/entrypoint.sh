#!/usr/bin/env bash

sh -c "
   echo 'Sleeping 20 seconds before running setup script'
   sleep 20s

   echo 'Starting setup script'

   /usr/config/configure-db.sh

    echo 'Finished setup script'

    " &
    exec /opt/mssql/bin/sqlservr

