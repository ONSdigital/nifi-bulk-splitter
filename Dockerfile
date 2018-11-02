FROM apache/nifi:1.7.1
COPY nifi-splitter-nar/target/nifi-splitter-nar-1.0-SNAPSHOT.nar /opt/nifi/nifi-1.7.1/lib/
COPY ai-bulk.xml /opt/nifi/nifi-1.7.1/conf/templates
EXPOSE 8080
