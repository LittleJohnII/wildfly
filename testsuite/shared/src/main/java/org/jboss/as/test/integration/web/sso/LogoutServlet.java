package org.jboss.as.test.integration.web.sso;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * A servlet that logs out a user by calling {@link HttpServletRequest#logout()}.
 *
 * @author Richard Janík
 */
public class LogoutServlet extends HttpServlet {

    private static final long serialVersionUID = 1723766328242371390L;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            request.logout();
        }
        response.sendRedirect(request.getContextPath() + "/index.html");
    }
}
