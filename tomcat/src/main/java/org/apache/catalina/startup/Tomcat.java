package org.apache.catalina.startup;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.servlet.ServletManger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Tomcat {

    private static final Logger log = LoggerFactory.getLogger(Tomcat.class);

    private final ServletManger servletManger;

    public Tomcat(ServletManger servletManger) {
        this.servletManger = servletManger;
    }

    public void start() {
        var connector = new Connector(servletManger);
        connector.start();

        try {
            // make the application wait until we press any key.
            System.in.read();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            log.info("web server stop.");
            connector.stop();
        }
    }
}
