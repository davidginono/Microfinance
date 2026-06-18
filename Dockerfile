FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM tomcat:10.1-jre21-temurin AS final

RUN rm -rf /usr/local/tomcat/webapps/*
RUN sed -i '/<\/Host>/i\        <Valve className="org.apache.catalina.valves.RemoteIpValve" remoteIpHeader="x-forwarded-for" protocolHeader="x-forwarded-proto" protocolHeaderHttpsValue="https" />' /usr/local/tomcat/conf/server.xml

COPY --from=build /workspace/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
