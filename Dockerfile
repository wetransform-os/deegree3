FROM tomcat:8.0-jre7
ENV LANG en_US.UTF-8

EXPOSE 8080

# set default secrets ( override for production use! )
# consoleSecretKey="deegree"
ENV consoleSecretKey=000001544E797221:564344F65B8F9DDBA6A410E461E7801E10955F56D8679284966F400C68B6CEAB 
ENV apiUser=deegree
ENV apiPass=deegree

RUN mkdir /root/.deegree && \
  mkdir /build && \
  rm -r /usr/local/tomcat/webapps/ROOT   

COPY ./ /build/

# build deegree
RUN apt-get update && apt-get install -y --no-install-recommends default-jdk maven && \
  cd /build/ && \
  mvn install -DskipTests && \
  cp /build/deegree-services/deegree-webservices/target/deegree-webservices-*.war /usr/local/tomcat/webapps/ROOT.war && \
  cd / && \
  rm -r /build/ && rm -r /root/.m2 && \
  apt-get purge -y --auto-remove default-jdk maven && \
  apt-get clean && rm -rf /var/lib/apt/lists/*

#cmd:
# 1. configure deegreeapi access
# 2. configure console access
# 3. start tomcat
CMD  sed -i '44i <user username="'"$apiUser"'" password="'"$apiPasswd"'" roles="deegree" \/> /' /usr/local/tomcat/conf/tomcat-users.xml \
     && echo $consoleSecretKey >/root/.deegree/console.pw \
     && /usr/local/tomcat/bin/catalina.sh run
