FROM apache/nifi:1.7.1
COPY nifi-splitter-nar/target/nifi-splitter-nar-1.0-SNAPSHOT.nar /opt/nifi/nifi-1.7.1/lib/
COPY ai-bulk.xml /opt/nifi/nifi-1.7.1/conf/templates

USER root
RUN mkdir -p /usr/config

COPY lib/mssql-jdbc-7.0.0.jre8.jar /usr/config

USER nifi

EXPOSE 8080
LABEL vendor=ONS
LABEL description="NiFi image with ONS custom processors and flow"
