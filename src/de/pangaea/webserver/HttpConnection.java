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

import java.net.*;
import java.util.*;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class HttpConnection extends java.lang.Thread implements HttpServletRequest,HttpServletResponse {

    public HttpConnection(WebServer parent, Socket sock) {
        super("Client Connection: "+sock);
        this.sock=sock;
        this.parent=parent;
        parent.connectionCount++;
        start();
    }

    public void run() {
        try {
            boolean ok=true;
            InputStream origin=new BufferedInputStream(sock.getInputStream());
            out=new ServletOutputStream(new BufferedOutputStream(sock.getOutputStream()),this);

            initHeaders();

            // read REQUEST
            DataInputStream dis=new DataInputStream(origin);
            String request,line;
            while ((request=dis.readLine()).equals(""));
            MyStringTokenizer st=new MyStringTokenizer(request,' ');
            method=st.nextToken().toUpperCase().trim();
            requestURI=st.nextToken().trim();
            proto=st.nextToken().toUpperCase().trim();

            // read headers
            int pos;
            while (!(line=dis.readLine()).equals("")) {
                pos=line.indexOf(':');
                if (pos>=0) {
                    String key=line.substring(0,pos).trim().toLowerCase();
                    String value=line.substring(pos+1).trim();
                    //if (value.startsWith("\"") && value.endsWith("\"")) value=value.substring(1,value.length()-1);
                    List v=(List)reqheaders.get(key);
                    if (v==null) reqheaders.put(key,v=new ArrayList());
                    v.add(value);
                }
            }

            // disable output if method is HEAD
            if (method.equals("HEAD")) out.disableOutput();

            // init input stream by Content-Length, analyze POST data description
            inpContentLength=getIntHeader("content-length");
            in=new ServletInputStream(origin,inpContentLength);
            String inpContentType=getContentType();
            if (method.equals("POST") && inpContentLength<0) {
                sendError(HttpServletResponse.SC_LENGTH_REQUIRED,"Missing Content-Length in request!");
                ok=false;
            }
            isPostParams=(method.equals("POST") && inpContentType!=null && inpContentType.toLowerCase().equals("application/x-www-form-urlencoded"));

            // input Charset
            String charset=guessCharset(inpContentType);
            if (charset!=null) reqcharset=charset;

            // check protocol
            if (ok) {
                int slash=proto.indexOf('/');
                int dot=proto.indexOf('.');
                if (dot<slash || slash<0 || dot<0) {
                    sendError(HttpServletResponse.SC_BAD_REQUEST,"Your browser sent an invalid request (HTTP version invalid)!");
                    ok=false;
                } else {
                    int majorversion=0;
                    try {
                        majorversion=Integer.parseInt(proto.substring(slash+1,dot));
                    } catch (NumberFormatException e) {}
                    if (!proto.substring(0,slash).toUpperCase().equals("HTTP") || majorversion<1) {
                        sendError(HttpServletResponse.SC_BAD_REQUEST,"Your browser sent an invalid request (HTTP version invalid)!");
                        ok=false;
                    }
                }
            }

            // read cookies
            if (ok) {
                Enumeration en=getHeaders("cookie");
                while (en!=null && en.hasMoreElements()) {
                    String header=(String)en.nextElement();

                    // code from Apache Tomcat... Thanks
                    while (header.length() > 0) {
                        int semicolon = header.indexOf(';');
                        if (semicolon < 0)
                            semicolon = header.length();
                        if (semicolon == 0)
                            break;
                        String token = header.substring(0, semicolon);
                        if (semicolon < header.length())
                            header = header.substring(semicolon + 1);
                        else
                            header = "";
                        try {
                            int equals = token.indexOf('=');
                            if (equals > 0) {
                                String name = token.substring(0, equals).trim();
                                String value = token.substring(equals+1).trim();
                                cookies.add(new Cookie(name, value));
                            }
                        } catch (Throwable e) {
                            ;
                        }
                    }
                }
            }

            // convert path
            if ((pos=requestURI.indexOf('#'))>=0) requestURI=requestURI.substring(0,pos);
            if (ok) try {
                URL url=new URL(requestURI);
                requestURI=url.getFile();
                if (!url.getProtocol().toUpperCase().equals("HTTP")) {
                    sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid protocol in request: "+url.getProtocol());
                    ok=false;
                }
            } catch (MalformedURLException e) {}
            if (ok && !requestURI.startsWith("/")) {
                sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid path info: "+requestURI);
                ok=false;
            }
            if (ok && (pos=requestURI.indexOf('?'))>=0) {
                queryString=requestURI.substring(pos+1);
                requestURI=requestURI.substring(0,pos);
            }

            if (ok && "/".equals(requestURI) && (method.equals("GET") || method.equals("HEAD"))) {
                String rurl=(String)parent.getAttribute("server.redirecthome");
                if (rurl!=null) {
                    sendRedirect(rurl);
                    ok=false;
                }
            }

            if (ok) {
                try {
                    // servlet laden und starten von service()
                    String mapping=null,decodedRequestURI=StringUtils.utf8decodeURIraw(requestURI);
                    servletPath=decodedRequestURI;
                    Iterator it=parent.uriMappings.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry e=(Map.Entry)it.next();
                        String map=(String)e.getKey();
                        if (map.endsWith("/*")) {
                            map=map.substring(0,map.length()-2);
                            if (decodedRequestURI.startsWith(map+'/') || decodedRequestURI.equals(map)) {
                                mapping=(String)e.getValue();
                                pathInfo=StringUtils.utf8decodeURIraw(requestURI.substring(map.length()));
                                if (pathInfo.equals("")) pathInfo=null;
                                servletPath=requestURI.substring(0,map.length());
                                break;
                            }
                        } else if (map.startsWith("*")) {
                            map=map.substring(1);
                            if (decodedRequestURI.endsWith(map)) {
                                mapping=(String)e.getValue();
                                break;
                            }
                        } else if (map.equals(decodedRequestURI) && map.indexOf('*')<0) {
                            mapping=(String)e.getValue();
                            break;
                        }
                    }
                    Servlet servlet;
                    if (mapping==null) {
                        servlet=parent.loadServletClass(SendFileServlet.class.getName());
                    } else {
                        servlet=parent.loadServlet(mapping);
                        if (servlet==null) {
                            sendError(HttpServletResponse.SC_NOT_FOUND,"Could not find servlet: "+mapping);
                            ok=false;
                        }
                    }
                    if (ok) {
                        if (servlet instanceof javax.servlet.SingleThreadModel)
                            synchronized(servlet) { servlet.service((HttpServletRequest)this,(HttpServletResponse)this); }
                        else
                            servlet.service((HttpServletRequest)this,(HttpServletResponse)this);
                    }
                } catch (Exception e) {
                    StringWriter stacktrace=new StringWriter();
                    e.printStackTrace(new PrintWriter(stacktrace,true));
                    try {
                        sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"An exception occured during request:<PRE>"+stacktrace.toString()+"</PRE>");
                    } catch (IllegalStateException e1) {
                        out.println("\n<p>An exception occured during request:<PRE>"+stacktrace.toString()+"</PRE>");
                    }
                }
            }
        } catch (Exception e) {
            parent.log("Error during client connection:",e);
        }

        // close streams
        try {
            if (in!=null) in.close();
            if (out!=null) {
                if (_printwriter!=null) _printwriter.flush();
                int len=out.getContentLength();
                if (!committed && len>=0 && !containsHeader("content-length")) setContentLength(len);
                out.close();
                out.out.close(); // close original stream
            }
            try { in.in.close(); } catch (Exception e1) {} // close original stream
        } catch (Exception e) {
            parent.log("Error closing client connection:",e);
        }

        // logfile
        if (out!=null) parent.logAccess(getRemoteHost(),reqDate,method,
            (queryString!=null)?(requestURI+"?"+queryString):(requestURI),
            proto,status,out.getBytesWritten(),getHeader("referer"),getHeader("user-agent"));

        parent.connectionCount--;
    }

    protected void initHeaders() {
        setHeader("Server",parent.getServerInfo());
        setDateHeader("Date",reqDate.getTime());
        setHeader("Content-Type","text/html; charset=ISO-8859-1");
        setHeader("Connection","close");
        if (parent.preventCaching) {
            setHeader("Cache-Control","no-store, no-cache, must-revalidate, post-check=0, pre-check=0");
            setHeader("Pragma","no-cache");
            setHeader("Expires","now");
        }
    }

    protected void writeHeaders(OutputStream out) {
        if (in!=null) try { in.close(); } catch (IOException e) {}
        PrintStream ps=new PrintStream(out,true);
        ps.print("HTTP/1.0 "+status+" "+StringUtils.getStatusString(status)+"\r\n"); // everytime print a HTTP/1.0 header
        Iterator it=respheaders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry=(Map.Entry)it.next();
            String name=(String)entry.getKey();
            List hd=(List)entry.getValue();

            //convert name that it is "nice"
            boolean upcase=true; StringBuffer name2=new StringBuffer();
            for (int i=0; i<name.length(); i++) {
                char ch=name.charAt(i);
                if (upcase) ch=Character.toUpperCase(ch);
                name2.append(ch);
                upcase=((ch<'A' || ch>'Z') && (ch<'a' || ch>'z'));
            }

            //print headers
            for (int i=0; i<hd.size(); i++) {
                ps.print(name2+": "+hd.get(i)+"\r\n");
            }
        }
        ps.print("\r\n");
        ps.flush();
    }

    private String guessCharset(String s1) {
        if (s1==null) return null;
        s1=s1.toLowerCase();
        int p1=s1.indexOf(";");
        int p2=s1.indexOf("charset",p1+1);
        int p3=s1.indexOf("=",p2+7);
        if (p3>p2 && p2>p1 && p1>0) {
            String charset=s1.substring(p3+1).trim();
            if (charset.charAt(0)=='\"') {
                charset=charset.substring(1);
                if ((p1=charset.indexOf("\""))>=0) charset=charset.substring(0,p1);
            }
            return charset;
        } else return null;
    }

    // Interface Methods for servlets

    // ******************* HttpServletRequest

    public Object getAttribute(String s) { return reqattributes.get(s); }

    public Enumeration getAttributeNames() { return Collections.enumeration(reqattributes.keySet()); }

    public String getCharacterEncoding() {
        return reqcharset;
    }

    public int getContentLength() { return inpContentLength; }

    public String getContentType() { return getHeader("content-type"); }

    public javax.servlet.ServletInputStream getInputStream() throws IOException {
        if (_bufferedreader!=null || doneReadPostParams) throw new IllegalStateException();
        inOpened=true; return in;
    }

    public Locale getLocale() { return Locale.getDefault(); }

    public Enumeration getLocales() {
        return Collections.enumeration(Collections.singletonList(Locale.getDefault()));
    }

    private void parseQueryString(String s) {
        if (s == null) throw new IllegalArgumentException();
        String charset=getCharacterEncoding();
        StringTokenizer st = new StringTokenizer(s, "&");
        while (st.hasMoreTokens()) {
            String pair = (String) st.nextToken();
            int pos = pair.indexOf('=');
            if (pos == -1) continue;
            String key,val;
            try {
                key = URLDecoder.decode(pair.substring(0, pos), charset);
                val = URLDecoder.decode(pair.substring(pos + 1, pair.length()), charset);
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            String valArray[] = null;
            if (reqparams.containsKey(key)) {
                String oldVals[] = (String[]) reqparams.get(key);
                valArray = new String[oldVals.length + 1];
                for (int i = 0; i < oldVals.length; i++)
                    valArray[i] = oldVals[i];
                valArray[oldVals.length] = val;
            } else {
                valArray = new String[1];
                valArray[0] = val;
            }
            reqparams.put(key, valArray);
        }
    }

    private void parsePostData() {
        if (isPostParams && !doneReadPostParams) {
            try {
                InputStream in=getInputStream();
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            doneReadPostParams=true;
            int len=getContentLength();
            if (len <= 0) return;

            byte[] postedBytes = new byte[len];
            try {
                int offset = 0;
                do {
                    int inputLen = in.read(postedBytes, offset, len - offset);
                    if (inputLen <= 0) throw new IllegalArgumentException("POST data input stream ends before Content-Length!");
                    offset += inputLen;
                } while ((len - offset) > 0);

            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage());
            }

            try {
                String postedBody = new String(postedBytes, 0, len, getCharacterEncoding());
                parseQueryString(postedBody);
            } catch (java.io.UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }
    }

    public String getParameter(String s) {
        if (!queryStringParsed && queryString!=null) parseQueryString(queryString);
        queryStringParsed=true;
        parsePostData();
        String[] params=getParameterValues(s);
        return (params==null)?null:params[0];
    }

    public Enumeration getParameterNames() {
        if (!queryStringParsed && queryString!=null) parseQueryString(queryString);
        queryStringParsed=true;
        parsePostData();
        return Collections.enumeration(reqparams.keySet());
    }

    public java.util.Map getParameterMap() {
        if (!queryStringParsed && queryString!=null) parseQueryString(queryString);
        queryStringParsed=true;
        parsePostData();
        return Collections.unmodifiableMap(reqparams);
    }

    public String[] getParameterValues(String s) {
        if (!queryStringParsed && queryString!=null) parseQueryString(queryString);
        queryStringParsed=true;
        parsePostData();
        return (String[])reqparams.get(s);
    }

    public String getProtocol() { return proto; }

    public BufferedReader getReader() throws IOException,IllegalStateException {
        if (inOpened) throw new IllegalStateException();
        if(_bufferedreader == null) {
            _bufferedreader = new BufferedReader(new InputStreamReader(in, reqcharset));
        }
        return _bufferedreader;
    }

    /**
     * @deprecated Method getRealPath is deprecated
     */
    public String getRealPath(String s) { return parent.getRealPath(s); }

    public java.lang.StringBuffer getRequestURL() {
        return HttpUtils.getRequestURL(this);
    }

    public String getRemoteAddr() {
        byte[] addr=sock.getInetAddress().getAddress();
        StringBuffer sb=new StringBuffer();
        for (int i=0; i<4; i++) {
            int b=addr[i];
            if (b<0) b+=256;
            sb.append(b);
            if (i<3) sb.append('.');
        }
        return sb.toString();
    }

    public String getRemoteHost() {
        return (remoteHost==null)?(remoteHost=sock.getInetAddress().getHostName()):remoteHost;
    }

    public RequestDispatcher getRequestDispatcher(String s) { return parent.getRequestDispatcher(s); }

    public String getScheme() { return "http"; }

    public String getServerName() {
        String name=getHeader("host");
        if (name!=null) {
            int i=name.indexOf(':');
            if (i>=0) name=name.substring(0,i);
            return name;
        } else return parent.hostName;
    }

    public int getServerPort() {
        String name=getHeader("host");
        if (name!=null) {
            int i=name.indexOf(':');
            int port=80;
            if (i>=0) try {
                port=Integer.parseInt(name.substring(i+1));
            } catch (NumberFormatException e) {}
            return port;
        } else return parent.port;
    }

    public boolean isSecure() { return false; }

    public void removeAttribute(String s) { reqattributes.remove(s); }

    public void setAttribute(String s, Object obj) { reqattributes.put(s,obj); }

    public String getAuthType() { return null; }

    public String getContextPath() { return ""; }

    public Cookie[] getCookies() {
        if (cookies.size()==0) return null;
        else {
            Cookie acookie[] = new Cookie[cookies.size()];
            for(int j = 0; j < acookie.length; j++) acookie[j] = (Cookie)cookies.get(j);
            return acookie;
        }
    }

    public long getDateHeader(String s) {
        long l = -1L;
        String s1 = getHeader(s);
        if(s1 != null)
        {
            int i = s1.indexOf(';');
            if(i != -1)
                s1 = s1.substring(0, i);
            try
            {
                l = Date.parse(s1);
            }
            catch(Exception _ex) { }
            if(l == -1L)
                throw new IllegalArgumentException();
        }
        return l;
    }

    public String getHeader(String s) {
        List v=(List)reqheaders.get(s.toLowerCase());
        return (v==null)?null:((String)v.get(0));
    }

    public Enumeration getHeaderNames() { return Collections.enumeration(reqheaders.keySet()); }

    public Enumeration getHeaders(String s) {
        List v=(List)reqheaders.get(s.toLowerCase());
        return (v==null)?null:Collections.enumeration(v);
    }

    public int getIntHeader(String s) {
        String hd=getHeader(s);
        if (hd!=null) try {
            return Integer.parseInt(hd);
        } catch (NumberFormatException ne) {
            return -1;
        } else return -1;
    }

    public String getMethod() { return method; }

    public String getPathInfo() { return pathInfo; }

    public String getPathTranslated() { return (pathInfo==null)?null:parent.getRealPath(pathInfo); }

    public String getQueryString() { return queryString; }

    public String getRemoteUser() { return null; }

    public String getRequestURI() { return requestURI; }

    public String getRequestedSessionId() { return null; /*todo*/ }

    public String getServletPath() { return servletPath; }

    public HttpSession getSession() { return null; }

    public HttpSession getSession(boolean flag) { return null; }

    public java.security.Principal getUserPrincipal() { return null; }

    public boolean isRequestedSessionIdFromCookie() { return false; }

    public boolean isRequestedSessionIdFromURL() { return false; }

    /**
     * @deprecated Method isRequestedSessionIdFromUrl is deprecated
     */
    public boolean isRequestedSessionIdFromUrl() { return isRequestedSessionIdFromURL(); }

    public boolean isRequestedSessionIdValid() { return false; }

    public boolean isUserInRole(String s) { return false; }

    public void setCharacterEncoding(java.lang.String encoding) throws java.io.UnsupportedEncodingException {
        if (inOpened) throw new IllegalStateException();
        reqcharset=encoding;
    }

    // ******************* HttpServletResponse

    public void flushBuffer() throws IOException {
        if (_printwriter!=null) _printwriter.flush();
        out.flushInternal();
    }

    public int getBufferSize() { return out.getBufSize(); }

    public javax.servlet.ServletOutputStream getOutputStream() throws IOException {
        if (_printwriter!=null) throw new IllegalStateException();
        outOpened=true; return out;
    }

    public PrintWriter getWriter()
        throws IOException
    {
        if(_printwriter == null)
        {
            if(outOpened)
                throw new IllegalStateException();
            _printwriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, respcharset)), false);
        }
        return _printwriter;
    }

    public boolean isCommitted() { return committed; }

    public void resetBuffer() {
        if (!committed) {
            if (_printwriter!=null) _printwriter.flush();
            out.reset();
            status=HttpServletResponse.SC_OK;
            respheaders.clear();
            initHeaders();
        } else throw new IllegalStateException();
    }

    public void reset() { resetBuffer(); }

    public void setBufferSize(int i) { out.setBufSize(i); }

    public void setContentLength(int i) { setHeader("Content-Length",Integer.toString(i)); }

    public void setContentType(String s) { setHeader("Content-Type",s); }

    public void setLocale(Locale locale) { /*todo*/ }

    public void addCookie(Cookie cookie) {
        addHeader(CookieTools.getCookieHeaderName(cookie), CookieTools.getCookieHeaderValue(cookie));
    }

    public void addDateHeader(String s, long l) {
        synchronized(headerDateFormat) {
            addHeader(s, headerDateFormat.format(new Date(l))+" GMT");
        }
    }

    public void addHeader(String s, String s1) {
        if (committed) throw new IllegalStateException();
        List hd;
        s=s.toLowerCase();
        if (s.equals("content-type")) { //determine charset
            if ((respcharset=guessCharset(s1))==null) {
                respcharset="ISO-8859-1";
                if (s1.startsWith("text/")) s1=s1+"; charset=ISO-8859-1";
            }
        }
        if (!containsHeader(s)) {
            hd=new ArrayList();
            respheaders.put(s,hd);
        } else hd=(List)respheaders.get(s);
        hd.add(s1);
    }

    public void addIntHeader(String s, int i) { addHeader(s,Integer.toString(i)); }

    public boolean containsHeader(String s) { return respheaders.containsKey(s.toLowerCase()); }

    public String encodeRedirectURL(String s) { return s; }

    /**
     * @deprecated Method encodeRedirectUrl is deprecated
     */
    public String encodeRedirectUrl(String s) { return encodeRedirectURL(s); }

    public String encodeURL(String s) { return s; }

    /**
     * @deprecated Method encodeUrl is deprecated
     */
    public String encodeUrl(String s) { return encodeURL(s); }

    public void sendError(int i) throws IOException { sendError(i,null); }

    public void sendError(int i, String errmsg) throws IOException {
        if (committed) throw new IllegalStateException();
        reset();
        setStatus(i); setContentType("text/html; charset=ISO-8859-1");
        if (errmsg==null) errmsg="This server has encountered an error which prevents it from fulfilling your request.";
        out.println("<H1>"+status+" "+StringUtils.getStatusString(status)+"</H1><p>"+errmsg);
    }

    public void sendRedirect(String s) throws IOException {
        if (committed) throw new IllegalStateException();
        reset();
        String port=(getServerPort()!=80)?(":"+getServerPort()):"";
        try {
            URL url=new URL(new URL("http://"+getServerName()+port+getServletPath()),s);
            s=url.toString();
        } catch (MalformedURLException e) {
            throw new MalformedURLException("Invalid URL to redirect: "+e.getMessage());
        }
        sendError(HttpServletResponse.SC_MOVED_TEMPORARILY,"The document can be found at <a href=\""+s+"\">"+s+"</a>");
        setHeader("Location",s);
    }

    public void setDateHeader(String s, long l) {
        if (containsHeader(s)) respheaders.remove(s.toLowerCase());
        addDateHeader(s,l);
    }

    public void setHeader(String s, String s1) {
        if (containsHeader(s)) respheaders.remove(s.toLowerCase());
        addHeader(s,s1);
    }

    public void setIntHeader(String s, int i) {
        if (containsHeader(s)) respheaders.remove(s.toLowerCase());
        addIntHeader(s,i);
    }

    public void setStatus(int i) { setStatus(i,null); }

    /**
     * @deprecated Method setStatus is deprecated
     */
    public void setStatus(int i, String s) {
        if (committed) throw new IllegalStateException();
        status=i;
    }

    private Socket sock;
    private WebServer parent;
    private ServletInputStream in;
    private ServletOutputStream out;
    private boolean inOpened=false;
    private boolean outOpened=false;
    private BufferedReader _bufferedreader=null;
    private PrintWriter _printwriter=null;
    private String respcharset="ISO-8859-1";
    private String reqcharset="ISO-8859-1";

    private String method="GET";
    private String proto="HTTP/1.0";

    private String requestURI="/";
    private String pathInfo=null;
    private String servletPath="";
    private String remoteHost=null;

    private String queryString=null;
    private boolean queryStringParsed=false;
    private Map reqheaders=new HashMap();
    private Map respheaders=new HashMap();
    private Map reqparams=new HashMap();
    private Map reqattributes=new HashMap();
    private List cookies=new ArrayList();
    private boolean isPostParams=false,doneReadPostParams=false;

    private int status=HttpServletResponse.SC_OK;
    private String errmsg=null;
    protected boolean committed=false;
    private Date reqDate=new Date();

    private int inpContentLength=0;

    private static DateFormat headerDateFormat=null;
    static {
        headerDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss",Locale.US);
        headerDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
}
