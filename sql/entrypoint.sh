#!/usr/bin/env bash

sh -c "/usr/config/wait-for-sqlserver.sh db /usr/config/configure-db.sh" & exec /opt/mssql/bin/sqlservr