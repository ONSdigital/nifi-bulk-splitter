#!/usr/bin/env bash

sh -c "
echo 'Starting DB Setup'
/opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P "yourStrong123Password" -d master -i setup.sql
echo 'DB setup finished'
"