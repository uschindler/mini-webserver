/*
 *   Copyright 2007 panFMP Developers Team c/o Uwe Schindler
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package de.pangaea.webserver;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;

public class ShutDownServlet extends HttpServlet {

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,java.io.IOException
    {
        resp.setHeader("cache-control","no-cache");
        resp.setDateHeader("expires",0L); // expire in the past, he-he

        PrintWriter out=resp.getWriter();
        ServletContext cx=getServletContext();
        if (req.getRemoteAddr().equals("127.0.0.1")) {
            if (cx instanceof de.pangaea.webserver.WebServer) {
                ((de.pangaea.webserver.WebServer)cx).quit();
                out.println("<HTML><HEAD><TITLE>Shutdown</TITLE></HEAD><BODY>");
                out.println("<H2>Shutting down webserver & database...</H2>");
                out.println("<script language=\"JavaScript\"><!--");
                out.println(" window.close();");
                out.println("// --></SCRIPT>");
                out.println("</BODY></HTML>");
            } else throw new ServletException("Cannot shutdown server with this servlet (invalid WebServer class)");
        } else {
                out.println("<HTML><HEAD><TITLE>Shutdown</TITLE></HEAD><BODY>");
                out.println("<H2>Shutting down only for local users!</H2>");
                out.println("<script language=\"JavaScript\"><!--");
                out.println(" window.close();");
                out.println("// --></SCRIPT>");
                out.println("</BODY></HTML>");
        }
    }

    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,java.io.IOException
    {
        doGet(req,resp);
    }

    public String getServletInfo() {
        return "Webserver Shutdown Servlet";
    }

}
