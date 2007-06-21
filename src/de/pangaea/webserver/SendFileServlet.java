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
import java.net.*;
import java.util.*;

public class SendFileServlet extends HttpServlet {

    public Set forceSaveAs=new HashSet(), indexFiles=new HashSet();

    public void init() throws ServletException {
        super.init();

        String s=(String)getServletContext().getAttribute("server.force-saveas");
        if (s!=null) {
            StringTokenizer st=new StringTokenizer(s,",");
            while (st.hasMoreTokens()) {
                String t=st.nextToken().trim();
                if (!"".equals(t)) forceSaveAs.add(t);
            }
        }

        s=(String)getServletContext().getAttribute("server.indexfiles");
        if (s==null) s="index.html";
        StringTokenizer st=new StringTokenizer(s,",");
        while (st.hasMoreTokens()) {
            String t=st.nextToken().trim();
            if (!"".equals(t)) indexFiles.add(t);
        }
    }

    private File getFile(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        String uri=req.getRequestURI();
        String path=req.getRealPath(StringUtils.utf8decodeURI(uri));
        File file=new File(path);

        if (uri.toUpperCase().startsWith("/WEB-INF/")) {
            if (resp!=null) resp.sendError(HttpServletResponse.SC_FORBIDDEN,"The /WEB-INF directory is not accessible from HTTP user-agents!");
            return null;
        }

        if (file.isDirectory()) {
            if (!uri.endsWith("/")) {
                if (resp!=null) resp.sendRedirect(uri+"/");
                return null;
            } else {
                Iterator it=indexFiles.iterator();
                File file1=file;
                while (it.hasNext()) {
                    file1=new File(file,(String)it.next());
                    if (file1.exists()) break;
                }
                file=file1;
            }
        }

        if ((file==null || !FileOpener.exists(file)) && resp!=null) {
            if (resp!=null) resp.sendError(HttpServletResponse.SC_NOT_FOUND,"The requested object could not be found!");
            return null;
        }

        return file;
    }

    public long getLastModified(HttpServletRequest req)
    {
        if (req.getQueryString()!=null) return -1L;
        try {
            File file=getFile(req,null);
            if (file!=null && FileOpener.exists(file)) {
                long size=FileOpener.getLastModified(file);
                return (size>0L)?size:-1L;
            } else return -1L;
        } catch (IOException e) {
            return -1L;
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,java.io.IOException
    {
        File file=getFile(req,resp);
        if (file==null) return;

        // Content-Type and charset for text files
        boolean convertCharset=false;
        String defaultCharset=null;
        String ct=getServletContext().getMimeType(file.getName());
        if (ct.startsWith("text/")) {
            defaultCharset=(String)getServletContext().getAttribute("server.defaultTextFileCharset");
            if (defaultCharset==null) defaultCharset="ISO-8859-1";
            String origCharset=req.getParameter("src-charset");
            if (origCharset!=null) defaultCharset=origCharset;
            String destCharset=req.getParameter("charset");
            if (forceSaveAs.contains(ct))
                resp.setHeader("Content-Disposition","attachment; filename="+file.getName());
            if (destCharset!=null) {
                ct+="; charset="+destCharset;
                convertCharset=!defaultCharset.equalsIgnoreCase(destCharset);
            }
        }
        resp.setContentType(ct);

        if (!convertCharset) resp.setDateHeader("Last-Modified",FileOpener.getLastModified(file));
        long len=FileOpener.getSize(file);
        if (len>0 && !convertCharset) {
            resp.setHeader("Content-Length",Long.toString(len));
        } else len=0x10000;

        InputStream is=FileOpener.openFileInputStream(file);
        if (convertCharset) {
            PrintWriter out=resp.getWriter();
            Reader in=new InputStreamReader(is,defaultCharset);

            char[] ba=new char[0x8000]; // max. 64 KB
            try {
                for (;;) {
                    int anz=in.read(ba);
                    if (anz>0) out.write(ba,0,anz); else break;
                }
            } catch (EOFException e) {}
        } else {
            javax.servlet.ServletOutputStream out=resp.getOutputStream();

            byte[] ba=new byte[ (len>0x10000L) ? 0x10000 : (int)len ]; // max. 64 KB
            try {
                for (;;) {
                    int anz=is.read(ba);
                    if (anz>0) out.write(ba,0,anz); else break;
                }
            } catch (EOFException e) {}
        }
        is.close();
    }

    public String getServletInfo() {
        return "Servlet for static files";
    }

}
