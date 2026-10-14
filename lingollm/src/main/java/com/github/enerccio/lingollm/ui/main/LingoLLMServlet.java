package com.github.enerccio.lingollm.ui.main;

import com.vaadin.flow.server.VaadinServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.annotation.WebServlet;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Configurable;

import java.io.IOException;

@WebServlet(urlPatterns = "/*", name = "LingoLLMServlet", asyncSupported = true, loadOnStartup = 1,
        initParams = { @WebInitParam(name = "com.vaadin.safeUrlSchemes", value = "*")})
@Configurable
public class LingoLLMServlet extends VaadinServlet {
    private static final Logger log = Logger.getLogger(LingoLLMServlet.class);

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        try {
            super.service(req, res);
        } catch (Throwable t) {
            log.error(t.getMessage());
            log.debug(t.getMessage(), t);
            throw new ServletException(t);
        }
    }
}
