FROM wire/bots.runtime:latest

COPY target/jira.jar      /opt/jira/jira.jar
COPY certs/keystore.jks    /opt/jira/keystore.jks

WORKDIR /opt/jira

