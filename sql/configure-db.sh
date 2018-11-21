#!/usr/bin/env bash
 
/opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P "yourStrong123Password" -d master -i setup.sql
