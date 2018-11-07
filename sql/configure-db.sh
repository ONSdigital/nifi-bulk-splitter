
#run the setup script to create the DB and the schema in the DB

sleep 30

/opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P "yourStrong(!)Password" -d master -i setup.sql

