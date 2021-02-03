FROM navikt/java:14
COPY build/libs/app.jar /app/
ENV JAVA_OPTS='-Dlogback.configurationFile=logback.xml'
