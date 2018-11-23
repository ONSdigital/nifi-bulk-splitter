#!/bin/sh
# wait-for-sqlserver.sh

set -e

host="$1"
shift
cmd="$@"

until /opt/mssql-tools/bin/sqlcmd -S "$host" -U sa -P "yourStrong123Password" -d master; do
  >&2 echo "SqlServer is unavailable - sleeping"
  sleep 1
done

>&2 echo "SqlServer is up - running DB script"
exec $cmd