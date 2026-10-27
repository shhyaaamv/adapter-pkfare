FROM 683455398069.dkr.ecr.ap-south-1.amazonaws.com/verteil-java:25.0.0.36.2
COPY ./connector-pkfare-server/build/libs/connector-pkfare-server*.jar /opt/connector-pkfare-server.jar
EXPOSE 9022 7045