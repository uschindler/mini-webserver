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
import java.net.*;
import java.util.*;
import javax.servlet.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class WebServer implements ServletContext,Runnable {

    private static org.apache.commons.logging.LogFactory logfact=org.apache.commons.logging.LogFactory.getFactory();
    private org.apache.commons.logging.Log logger;

    public WebServer(String conffile) throws IOException {
        logger=logfact.getInstance(this.getClass());
        log("Loading webserver config...");
        props=new Properties();
        props.load(new BufferedInputStream(new FileInputStream(conffile)));
        props.setProperty("server.basePath",new File(conffile).getAbsoluteFile().getParent());
        init();
    }

    public WebServer(Properties props) throws IOException {
        logger=logfact.getInstance(this.getClass());
        this.props=props;
        init();
    }

    private void init() throws IOException {
        log("Initalizing webserver...");
        String accesslogfile=props.getProperty("server.accesslog");
        if (accesslogfile!=null) {
            accesslog=new PrintWriter(new FileWriter(accesslogfile),true);
        }
        port=Integer.parseInt(props.getProperty("server.port","80"));
        hostName=props.getProperty("server.hostname");
        setDocRoot(props.getProperty("server.docroot","."));
        localOnly=!props.getProperty("server.restrictLocal","0").equals("0");
        preventCaching=!props.getProperty("server.preventCaching","0").equals("0");

        // load servlet mappings
        String s=props.getProperty("server.servlets","");
        StringTokenizer st=new StringTokenizer(s,",");
        while (st.hasMoreTokens()) {
            String name=st.nextToken();
            s=props.getProperty("servlet."+name+".url-patterns");
            if (s==null) logger.warn("Missing url-patterns for servlet \""+name+"\" in config file. Servlet disabled therefore!");
            else {
                StringTokenizer st2=new StringTokenizer(s,",");
                if (!st2.hasMoreTokens()) logger.warn("Missing url-patterns for servlet \""+name+"\" in config file. Servlet disabled therefore!");
                while (st2.hasMoreTokens()) {
                    String mapping=st2.nextToken();
                    if (mapping.indexOf('*')>=0 && !(mapping.startsWith("*") ^ mapping.endsWith("/*")))
                        logger.warn("Invalid url-pattern for servlet \""+name+"\" in config file: "+mapping);
                    else
                        uriMappings.put(mapping,name);
                }
            }
        }
        if (logger.isDebugEnabled()) logger.debug("uriMappings="+uriMappings);

        try {
            if (hostName==null) hostName=InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {}
        if (hostName==null) hostName="127.0.0.1";

        (thread=new Thread(this,"Webserver Thread")).start();
    }

    private synchronized LoadedServletConfig internalLoadServletClass(String className, String name) throws ServletException {
        if (className.equals(name)) log("Trying to load servlet class \""+className+"\"...");
        else log("Trying to load servlet \""+name+"\" with servlet class \""+className+"\"...");
        Servlet servlet;
        try {
            Class servletClass=Class.forName(className);
            servlet=(Servlet)servletClass.newInstance();
        } catch (Throwable e) { log("Cannot load servlet class",e); return null; }
        return new LoadedServletConfig(this,name,servlet);
    }

    public synchronized Servlet loadServletClass(String className) throws ServletException {
        LoadedServletConfig loadedServlet=(LoadedServletConfig)servlets.get("generic/"+className);
        if (loadedServlet==null) {
            loadedServlet=internalLoadServletClass(className,className);
            if (loadedServlet!=null) servlets.put("generic/"+className,loadedServlet);
            else return null;
        }
        return loadedServlet.getServlet();
    }

    public synchronized Servlet loadServlet(String name) throws ServletException {
        LoadedServletConfig loadedServlet=(LoadedServletConfig)servletMappings.get(name);
        if (loadedServlet==null) {
            String className=props.getProperty("servlet."+name+".class");
            if (className==null) return null;
            loadedServlet=internalLoadServletClass(className,name);
            if (loadedServlet!=null) {
                servlets.put(name+"/"+className,loadedServlet);
                servletMappings.put(name,loadedServlet);
            } else return null;
        }
        return loadedServlet.getServlet();
    }

    public void run() {
        if (doQuit) {
            setStatus(STATUS_SHUTDOWN);
            shutdownThread.setPriority(Thread.MIN_PRIORITY);
            log("Got quit message. Waiting for connections to finish...");

            while (connectionCount>0) try { Thread.sleep(100); } catch (InterruptedException e) {}

            shutdownThread.setPriority(Thread.NORM_PRIORITY);
            log("Shutting down....");
            try {
                ss.close();
            } catch (IOException e) {}

            log("Stopped listening to port "+port);
            log("Destroying servlets...");
            synchronized(this) {
                Iterator it=servlets.values().iterator();
                while (it.hasNext()) {
                    LoadedServletConfig se=(LoadedServletConfig)it.next();
                    try {
                        se.destroy();
                    } catch (Throwable e) {
                        log("Failed to destroy servlet \""+se.getServletName()+"\"!",e);
                    }
                }
                servlets.clear(); // reset it
                servletMappings.clear();
                uriMappings.clear();
            }
            if (accesslog!=null) accesslog.close();
            setStatus(STATUS_KILLED);
            log("Server stopped.");
            shutdownThread=null;
        } else {
            log("Starting to listen at port "+port+"...");
            try {
                if (localOnly) ss=new ServerSocket(port,50,InetAddress.getByName("127.0.0.1"));
                else ss=new ServerSocket(port,50);
            } catch (java.net.BindException e) {
                logger.fatal("Not Listening. Port "+port+" is in use.");
                setStatus(STATUS_KILLED);
                return;
            } catch (IOException e) {
                logger.fatal("Cannot create server socket:",e);
                setStatus(STATUS_KILLED);
                return;
            }
            setStatus(STATUS_RUNNING);
            for (;;) {
                try {
                    Socket sock=ss.accept();
                    new HttpConnection(this,sock);
                } catch (IOException e) {
                    logger.fatal("Error during listening:",e);
                    quit();
                }
            }
            //thread=null; // should never be called *g*
        }
    }

    public void quit() {
        if (thread==null) throw new IllegalStateException("Webserver already shutting down");
        thread.stop(); // kill hard, cannot be done otherways...
        doQuit=true;
        thread=null;
        (shutdownThread=new Thread(this,"Quit waiter")).start();
    }

    public void setDocRoot(String docRoot) {
        this.docRoot=new File(props.getProperty("server.basePath","."),docRoot).getAbsoluteFile().toString();
        props.setProperty("server.docroot",this.docRoot);
        log("Setting document root: "+this.docRoot);
    }

    public String getDocRoot() {
        return docRoot;
    }

    // **** Interface ServletContext

    public Object getAttribute(String s) { return props.getProperty(s); }

    public Enumeration getAttributeNames() { return props.keys(); }

    public ServletContext getContext(String s) { return this; }

    public String getInitParameter(String s) { return props.getProperty("servlet-context-init."+s); }

    public Enumeration getInitParameterNames() {
        List params=new ArrayList();
        String prefix="servlet-context-init.";
        Iterator it=props.keySet().iterator();
        while (it.hasNext()) {
            String s=(String)it.next();
            if (s.startsWith(prefix)) params.add(s.substring(prefix.length()));
        }
        return Collections.enumeration(params);
    }

    public int getMajorVersion() { return 2; }

    public int getMinorVersion() { return 3; }

    public String getMimeType(String s) {
        int p=s.lastIndexOf('.');
        s=s.substring(p+1).toLowerCase(Locale.ENGLISH);
        return props.getProperty("extension."+s,"text/plain");
    }

    public String getRealPath(String s) {
        if (s.indexOf("../")>=0 || s.indexOf('|')>=0) return null;
        return (new File(docRoot,s.replace('/',java.io.File.separatorChar))).toString();
    }

    public RequestDispatcher getRequestDispatcher(String s) {
        return null; //TODO
    }

    public RequestDispatcher getNamedDispatcher(String s) { return null; /* not supported */ }

    public URL getResource(String s) throws MalformedURLException {
        return (new File(getRealPath(s))).toURL();
    }

    public InputStream getResourceAsStream(String s) {
        try {
            return FileOpener.openFileInputStream(new File(getRealPath(s)));
        } catch (IOException ioe) {
            return null;
        }
    }

    public java.util.Set getResourcePaths(java.lang.String prefix) { return null; }

    public String getServerInfo() { return PROGNAME+"/"+VERSION; }

    /**
     * @deprecated Method getServlet is deprecated
     */
    public Servlet getServlet(String s) throws ServletException { return null; }

    /**
     * @deprecated Method getServletNames is deprecated
     */
    public Enumeration getServletNames() { return Collections.enumeration(Collections.EMPTY_LIST); }

    /**
     * @deprecated Method getServlets is deprecated
     */
    public Enumeration getServlets() { return Collections.enumeration(Collections.EMPTY_LIST); }

    /**
     * @deprecated Method log is deprecated
     */
    public void log(Exception exception, String s) { log(s,(Throwable)exception); }

    public synchronized void log(String s) {
        logger.info(s);
    }

    public synchronized void log(String s, Throwable throwable) {
        logger.error(s,throwable);
    }

    public void removeAttribute(String s) { props.remove(s); }

    public void setAttribute(String s, Object obj) { props.put(s,obj.toString()); }

    public java.lang.String getServletContextName() { return ""; }

    // other routines

    public synchronized void logAccess(String remoteHost, Date reqDate, String method, String requestURI, String proto, int status, int bytesWritten, String referer, String userAgent) {
        if (accesslog==null) {
            StringBuffer req=new StringBuffer("Request from ");
            req.append(remoteHost);
            req.append(" processed: \"");
            req.append(method);
            req.append(' ');
            req.append(requestURI);
            req.append(' ');
            req.append(proto);
            req.append("\"; Status: ");
            req.append(status);
            req.append("; Bytes: ");
            req.append(bytesWritten);
            if (referer!=null) {
                req.append("; Referer: \"");
                req.append(referer);
                req.append('"');
            }
            if (userAgent!=null) {
                req.append("; User-Agent: \"");
                req.append(userAgent);
                req.append('"');
            }
            logger.info(req.toString());
        } else {
            StringBuffer req=new StringBuffer(remoteHost);
            req.append(" - - [");
            req.append(logfileDateFormat.format(reqDate));
            req.append("] ");
            req.append(method);
            req.append(' ');
            req.append(requestURI);
            req.append(' ');
            req.append(proto);
            req.append(' ');
            req.append(status);
            req.append(' ');
            req.append(bytesWritten);
            req.append(' ');
            req.append((referer==null)?"-":referer);
            req.append((userAgent==null)?" -":" \""+userAgent+"\"");
            accesslog.println(req);
        }
    }

    public synchronized int getStatus() {
        return status;
    }

    protected synchronized void setStatus(int status) {
        if (this.status!=status) {
            this.status=status;
            if (statuslistener!=null) statuslistener.webserverStatusChanged(status);
        }
    }

    public int getPort() {
        return port;
    }

    public synchronized void setWebServerEventListener(WebServerEventListener listener) {
        this.statuslistener=listener;
        if (listener!=null)
            for (int i=0; i<=status; i++)
                listener.webserverStatusChanged(i);
    }

    public void join() {
        try {
            if (thread!=null) thread.join();
            while (getStatus()!=STATUS_KILLED) {
                if (shutdownThread!=null) shutdownThread.join(); else Thread.sleep(100);
            }
        } catch (InterruptedException ie) {}
    }

    // main func

    public static void main(String[] args) {
        if (args.length==1) startFromJNI(args[0],false).join();
        else System.err.println("Command line: java "+WebServer.class.getName()+" config-file");
    }

    public static WebServer startFromJNI(String configfile, boolean disableLogging) {
        // when started from JNI disable optionally all logging
        if (disableLogging) {
            logfact.setAttribute("org.apache.commons.logging.Log",org.apache.commons.logging.impl.NoOpLog.class.getName());
            logfact.release();
        }

        try {
            return new WebServer(configfile);
        } catch (Exception e) {
            logfact.getInstance(WebServer.class).fatal("Failed to initialize webserver!",e);
            return null;
        }
    }

    public static final int STATUS_STARTUP=0;
    public static final int STATUS_RUNNING=1;
    public static final int STATUS_SHUTDOWN=2;
    public static final int STATUS_KILLED=3;

    public static final String VERSION=de.pangaea.webserver.Package.get().getImplementationVersion();
    public static final String PROGNAME=de.pangaea.webserver.Package.get().getImplementationTitle();

    protected int port;
    protected String hostName;
    protected String docRoot;
    protected boolean localOnly=false,preventCaching=false;

    private Thread thread=null;
    private Thread shutdownThread=null;
    protected volatile int connectionCount=0;
    private volatile boolean doQuit=false;
    private volatile int status=STATUS_STARTUP;
    private WebServerEventListener statuslistener=null;

    private PrintWriter accesslog=null;

    private Map servlets=new HashMap(),servletMappings=new HashMap();

    /* provide the servlet mappings sorted in following order:
    longer strings before shorter in general
    - first direct mappings
    - second mappings ending in '/*'
    - third mappings starting with '*'
    */
    protected SortedMap uriMappings=Collections.synchronizedSortedMap(new TreeMap(new Comparator() {
        public int compare(Object o1, Object o2) {
            long l1=((String)o1).length(),l2=((String)o2).length(),c=Math.max(l1,l2);
            String s1=(String)o1, s2=(String)o2;
            if (!s1.endsWith("/*")) l1*=c;
            if (!s2.endsWith("/*")) l2*=c;
            if (!s1.startsWith("*")) l1*=c*2;
            if (!s2.startsWith("*")) l2*=c*2;
            if (l2!=l1) return (int)((l2-l1)/Math.abs(l2-l1));
            else return s1.compareTo(s2);
        }
        public boolean equals(Object obj) {
            return (obj==this);
        }
    }));

    private ServerSocket ss=null;

    protected Properties props=null;

    private DateFormat logfileDateFormat=new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss z", Locale.US);
}