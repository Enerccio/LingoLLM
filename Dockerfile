FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build/lingollm

# Cache dependencies (the application's POM inherits from the parent POM in the repository root)
COPY pom.xml /build/pom.xml
COPY lingollm/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the application
COPY lingollm/src ./src
COPY .git /build/.git
RUN mvn clean package -DskipTests

FROM jetty:12-jdk25-eclipse-temurin

USER jetty
WORKDIR $JETTY_BASE

RUN java -jar "$JETTY_HOME/start.jar" --create-startd \
    --add-modules=server,http,ee11-deploy,ee11-websocket-jakarta,ee11-webapp,ee11-jsp

COPY --from=builder /build/lingollm/target/*.war $JETTY_BASE/webapps/ROOT.war

USER root
RUN mkdir -p /var/lingollm/.lingollm && chown -R jetty:jetty /var/lingollm
# no COPY --chmod: it needs BuildKit, which the classic builder of older docker/docker-compose v1 lacks
COPY docker-entrypoint.sh /usr/local/bin/lingollm-entrypoint.sh
RUN chmod 755 /usr/local/bin/lingollm-entrypoint.sh

# starts as root to fix the owner of the data folder (a bind mount created by Docker is owned by root), then runs
# Jetty as jetty, see docker-entrypoint.sh
EXPOSE 8080

# healthy when the app answers with a page (the login redirect counts); the image has no curl, so bash opens the socket.
# Vaadin takes a while to start, hence the long start period
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET / HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && head -n1 <&3 | grep -Eq "HTTP/1\.. (2|3)[0-9][0-9]"' || exit 1

ENTRYPOINT ["/usr/local/bin/lingollm-entrypoint.sh"]
CMD ["java", "-jar", "/usr/local/jetty/start.jar"]