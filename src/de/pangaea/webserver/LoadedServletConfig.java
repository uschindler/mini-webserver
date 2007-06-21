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

import java.util.*;

class LoadedServletConfig implements javax.servlet.ServletConfig {
    protected LoadedServletConfig(WebServer parent, String name, javax.servlet.Servlet servlet) throws javax.servlet.ServletException {
        this.parent=parent;
        this.name=name;
        this.servlet=servlet;
        servlet.init(this);
    }

    public String getInitParameter(String s) { return parent.props.getProperty("servlet."+name+".init-param."+s); }

    public Enumeration getInitParameterNames() {
        List params=new ArrayList();
        String prefix="servlet."+name+".init-param.";
        Iterator it=parent.props.keySet().iterator();
        while (it.hasNext()) {
            String s=(String)it.next();
            if (s.startsWith(prefix)) params.add(s.substring(prefix.length()));
        }
        return Collections.enumeration(params);
    }

    public javax.servlet.ServletContext getServletContext() { return parent; }

    public String getServletName() { return name; }

    protected javax.servlet.Servlet getServlet() { return servlet; }

    protected void destroy() { servlet.destroy(); }

    private String name;
    private javax.servlet.Servlet servlet;
    private WebServer parent;
}
