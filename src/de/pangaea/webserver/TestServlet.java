/*
 *   Copyright 2007-2008 panFMP Developers Team c/o Uwe Schindler
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

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;


/**
 * This servlet simply echos back the request line and
 * headers that were sent by the client.
 */
public class TestServlet extends HttpServlet {

    public void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        // all Methods call doGet(), HEAD implicitely through super.service that wraps with NoBodyResponse and then calls doGet
        if ("HEAD".equals(req.getMethod())) super.service(req,res);
        else doGet(req,res);
    }

    public void doGet (HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        if ("/cookietest".equals(req.getPathInfo())) {
            Cookie cookie=new Cookie("testcookie","lets cook something");
            cookie.setPath("/");
            res.addCookie(cookie);
        }

        res.setContentType("text/html; charset=UTF-8");
        PrintWriter out = res.getWriter();
        out.println("<html>");
        out.println("<head><title>TestServlet</title></head>");
        out.println("<body>");

        out.println("<h1>Requested URL:</h1>");
        out.println("<pre>");
        out.println (StringUtils.encodeHTML(HttpUtils.getRequestURL(req).toString()));
        out.println("</pre>");

        Enumeration e = getServletConfig().getInitParameterNames();
        if (e != null) {
            boolean first = true;
            while (e.hasMoreElements()) {
                if (first) {
                    out.println("<h1>Init parameters:</h1>");
                    out.println("<pre>");
                    first = false;
                    }
                String param = (String) e.nextElement();
                print(out,param,getInitParameter(param));
            }
            out.println("</pre>");
        }

        out.println("<h1>Request information:</h1>");
        out.println("<pre>");
        print(out, "Request method (getMethod)", req.getMethod());
        print(out, "Request URI (getRequestURI)", req.getRequestURI());
        print(out, "Request protocol (getProtocol)", req.getProtocol());
        print(out, "Servlet path (getServletPath)", req.getServletPath());
        print(out, "Path info (getPathInfo)", req.getPathInfo());
        print(out, "Path translated (getPathTranslated)", req.getPathTranslated());
        print(out, "Query string (getQueryString)", req.getQueryString());
        print(out, "Content length (getContentLength)", req.getContentLength());
        print(out, "Content type (getContentType)", req.getContentType());
        print(out, "Server name (getServerName)", req.getServerName());
        print(out, "Server port (getServerPort)", req.getServerPort());
        print(out, "Remote user (getRemoteUser)", req.getRemoteUser());
        print(out, "Remote address (getRemoteAddr)", req.getRemoteAddr());
        print(out, "Remote host (getRemoteHost)", req.getRemoteHost());
        print(out, "Authorization scheme (getAuthType)", req.getAuthType());
        out.println("</pre>");

        e = req.getHeaderNames();
        if (e.hasMoreElements()) {
            out.println("<h1>Request headers:</h1>");
            out.println("<pre>");
            while (e.hasMoreElements()) {
                String name = (String)e.nextElement();
                Enumeration e1=req.getHeaders(name);
                while (e1.hasMoreElements()) print(out,name,(String)e1.nextElement());
            }
            out.println("</pre>");
        }

        Cookie[] cookies = req.getCookies();
        if (cookies!=null && cookies.length>0) {
            out.println("<h1>Request cookies:</h1>");
            out.println("<pre>");
            for (int i=0, c=cookies.length; i<c; i++) {
                print(out,cookies[i].getName(),cookies[i].getValue());
            }
            out.println("</pre>");
        }

        e = req.getParameterNames();
        if (e.hasMoreElements()) {
            out.println("<h1>Servlet parameters:</h1>");
            out.println("<pre>");
            while (e.hasMoreElements()) {
                String name = (String)e.nextElement();
                String vals[] = (String []) req.getParameterValues(name);
                if (vals != null) {
                    for (int i = 0; i<vals.length; i++) print(out,name,vals[i]);
                }
            }
            out.println("</pre>");
        }

        out.println("</body></html>");
        }

        private void print(PrintWriter out, String name, String value) throws IOException {
            out.print(" " + name + ": ");
            out.println((value == null) ? "[none]" : StringUtils.encodeHTML(value));
        }

        private void print(PrintWriter out, String name, int value) throws IOException {
            out.print(" " + name + ": ");
            if (value == -1) {
                out.println("[none]");
            } else {
                out.println(value);
            }
    }

    public String getServletInfo() {
        return "A servlet that shows the request headers sent by the client";
    }
}