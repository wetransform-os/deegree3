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

# 1. create workspace dir
# 2. create app schemas dir
# 3. remove standard ROOT application
RUN mkdir /root/.deegree \
    && rm -r /usr/local/tomcat/webapps/ROOT   

COPY ./deegree-webservices.war /usr/local/tomcat/webapps/ROOT.war

# add build info - see https://github.com/opencontainers/image-spec/blob/master/annotations.md
ARG BUILD_DATE
ARG VCS_REF
ARG VCS_URL
LABEL org.opencontainers.image.created=$BUILD_DATE \
  org.opencontainers.image.source=$VCS_URL \
  org.opencontainers.image.revision=$VCS_REF

#cmd:
# 1. configure schemas directory mapping
# 2. configure deegreeapi access
# 3. configure console access
# 4. start tomcat

CMD sed -i 's/.*<\/Host>/<Context docBase="\/root\/.deegree\/ws_production\/appschemas" path="\/schemas"\/>  <\/Host>/' /usr/local/tomcat/conf/server.xml \
	&& sed -i 's/.*<\/tomcat-users>/<user username="'"$apiUser"'" password="'"$apiPasswd"'" roles="deegree" \/> <\/tomcat-users>/' /usr/local/tomcat/conf/tomcat-users.xml \ 
	&& echo $consoleSecretKey >/root/.deegree/console.pw \
    && mkdir -p /root/.deegree/ws_production/appschemas \
	&& /usr/local/tomcat/bin/catalina.sh run
