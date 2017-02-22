FROM tomcat:8
ENV LANG en_US.UTF-8

LABEL title="deegree ows services"
LABEL description="deegree ows services based on https://github.com/wetransform-os/deegree3/tree/master-3.3.18-we"

EXPOSE 8080

# set default secrets ( overwrite for production use! )

# consoleSecretKey="deegree"
ENV consoleSecretKey=000001544E797221:564344F65B8F9DDBA6A410E461E7801E10955F56D8679284966F400C68B6CEAB 
ENV apiUser=deegree
ENV apiPass=deegree

RUN mkdir /root/.deegree \
    && rm -r /usr/local/tomcat/webapps/ROOT   

COPY ./deegree-webservices.war /usr/local/tomcat/webapps/ROOT.war
    
#cmd:
# 1. configure deegreeapi access
# 2. configure console access
# 3. start tomcat
CMD  sed -i '44i <user username="'"$apiUser"'" password="'"$apiPasswd"'" roles="deegree" \/> /' /usr/local/tomcat/conf/tomcat-users.xml\ 
     && echo $consoleSecretKey >/root/.deegree/console.pw \
     && /usr/local/tomcat/bin/catalina.sh run
