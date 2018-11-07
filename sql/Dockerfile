
FROM microsoft/mssql-server-linux

# Create a config directory
RUN mkdir -p /usr/config

WORKDIR /usr/config

COPY . /usr/config

RUN chmod +x /usr/config/configure-db.sh

EXPOSE 1433

CMD  exec /bin/bash entrypoint.sh

#CMD [ "/opt/mssql/bin/sqlservr" ]

