#!/usr/bin/env bash
 
/opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P "yourStrong(!)Password" -d master -i setup.sql