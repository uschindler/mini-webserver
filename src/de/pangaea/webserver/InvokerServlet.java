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

public class InvokerServlet extends HttpServlet {

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servletPackagePrefix=config.getInitParameter("servletPackagePrefix");
        if (servletPackagePrefix==null) servletPackagePrefix="";
    }

    public void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException,java.io.IOException {
        WebServer ws=(WebServer)getServletContext();
        String pathInfo=req.getPathInfo();
        if (pathInfo==null || "/".equals(pathInfo) || "".equals(pathInfo)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND,"The requested object could not be found!");
        } else {
            int p=pathInfo.indexOf('/',1);
            String newPathInfo=null;
            String servletClass=null;
            if (p>=0) {
                newPathInfo=pathInfo.substring(p);
                pathInfo=pathInfo.substring(0,p);
            }
            servletClass=servletPackagePrefix+pathInfo.substring(1);
            InvokerServletRequestWrapper wrappedReq=new InvokerServletRequestWrapper(req,newPathInfo,req.getServletPath()+pathInfo);
            Servlet servlet=ws.loadServletClass(servletClass);
            if (servlet==null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND,"Cannot find servlet class \""+servletClass+"\"!");
            } else {
                if (servlet instanceof javax.servlet.SingleThreadModel)
                    synchronized(servlet) { servlet.service(wrappedReq,resp); }
                else
                    servlet.service(wrappedReq,resp);
            }
        }
    }

    public String getServletInfo() {
        return "Invoker Servlet for /servlet/* URL mapping";
    }

    protected String servletPackagePrefix="";

    private static final class InvokerServletRequestWrapper extends HttpServletRequestWrapper {

        public InvokerServletRequestWrapper(HttpServletRequest req, String pathInfo, String servletPath) {
            super(req);
            this.pathInfo=pathInfo;
            this.servletPath=servletPath;
        }

        public String getPathInfo() {
            return pathInfo;
        }

        public String getPathTranslated() {
            return (pathInfo==null)?null:getRealPath(pathInfo);
        }

        public String getServletPath() {
            return servletPath;
        }

        private String pathInfo,servletPath;

    }
}
