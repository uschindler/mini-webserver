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

// add your custom import statements here
import java.util.*;
import java.security.*;
import java.net.*;

public class StringUtils {

    public static String decodeURI(String s) {
        try {
            return URLDecoder.decode(s,"iso-8859-1");
        } catch (java.io.UnsupportedEncodingException e) { return null; }
    }

    public static String encodeURI(String s) {
        try {
            return URLEncoder.encode(s,"iso-8859-1");
        } catch (java.io.UnsupportedEncodingException e) { return null; }
    }

    public static String utf8decodeURI(String s) {
        try {
            return URLDecoder.decode(s,"UTF-8");
        } catch (java.io.UnsupportedEncodingException e) { return null; }
    }

    public static String utf8encodeURI(String s) {
        try {
            return URLEncoder.encode(s,"UTF-8");
        } catch (java.io.UnsupportedEncodingException e) { return null; }
    }

    public static String getStatusString(int status) {
        switch (status) {
            case 100: return "Continue";
            case 101: return "Switching Protocols";
            case 200: return "OK";
            case 201: return "Created";
            case 202: return "Accepted";
            case 203: return "Non-Authoritative Information";
            case 204: return "No Content";
            case 205: return "Reset Content";
            case 206: return "Partial Content";
            case 300: return "Multiple Choices";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 303: return "See Other";
            case 304: return "Not Modified";
            case 305: return "Use Proxy";
            case 307: return "Temporary Redirect";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 402: return "Payment Required";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 406: return "Not Acceptable";
            case 407: return "Proxy Authentication Required";
            case 408: return "Request Timeout";
            case 409: return "Conflict";
            case 410: return "Gone";
            case 411: return "Length Required";
            case 412: return "Precondition Failed";
            case 413: return "Request Entity Too Large";
            case 414: return "Request-URI Too Long";
            case 415: return "Unsupported Media Type";
            case 416: return "Requested Range Not Satisfiable";
            case 417: return "Expectation Failed";
            case 500: return "Internal Server Error";
            case 501: return "Not Implemented";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            case 505: return "HTTP Version Not Supported";
            default: return "??? Unknown Status";
        }
    }

    public static String encodeHTML(String s) {
        int l=s.length();
        StringBuffer dest=new StringBuffer(l);
        for (int i=0; i<l; i++) {
            char c=s.charAt(i);
            switch (c) {
                case '<': dest.append("&lt;");break;
                case '>': dest.append("&gt;");break;
                case '"': dest.append("&quot;");break;
                case '&': dest.append("&amp;");break;
                case '\n':dest.append("<br>\n");break;
                case '\r':break;
                default:  dest.append(c);
            }
        }
        return dest.toString();
    }

    public static String encodeHTMLWithLinks(String s, String linkTarget) {
        // first prefix all mails with mailto:
        int p,len=s.length();
        StringBuffer dest=new StringBuffer(len);
        StringBuffer part=new StringBuffer(len);
        boolean isMail=false;
        for (p=0; p<len; p++) {
            char ch=s.charAt(p);
            if (Character.isWhitespace(ch)) {
                if (isMail && part.length()>1) dest.append("mailto:");
                isMail=false;
                dest.append(part);
                dest.append(ch);
                part.setLength(0);
            } else {
                part.append(ch);
                if (ch=='@') isMail=true;
            }
        }
        if (isMail && part.length()>1) dest.append("mailto:");
        dest.append(part);
        s=dest.toString();

        // process links
        int p2,p3;
        p=0;
        len=s.length();
        dest=new StringBuffer(len);
        for (;;) {
            p2=s.indexOf("http://",p);
            p3=s.indexOf("ftp://",p); if (p3>=0 && (p3<p2 || p2<0)) p2=p3;
            p3=s.indexOf("mailto:",p); if (p3>=0 && (p3<p2 || p2<0)) p2=p3;
            p3=s.indexOf("doi:",p); if (p3>=0 && (p3<p2 || p2<0)) p2=p3;
            p3=s.indexOf("hdl:",p); if (p3>=0 && (p3<p2 || p2<0)) p2=p3;
            p3=s.indexOf("link:",p); if (p3>=0 && (p3<p2 || p2<0)) p2=p3;
            if (p2<0) break;

            dest.append(encodeHTML(s.substring(p,p2)));

            // scan links from beginning
            StringBuffer link=new StringBuffer();
            char ch='\0';
            while (p2<len) {
                ch=s.charAt(p2);
				if (!(Character.isWhitespace(ch) || Character.isSpaceChar(ch))) {
					link.append(ch); p2++;
				} else break;
            }
            // clean up links from the right side again (strip chars)
            int linklen=link.length();
            for (int i=linklen-1; i>=0; i--) {
                ch=link.charAt(i);
                if (!Character.isLetterOrDigit(ch)) {
					linklen--;
					p2--;
				} else break;
            }
            link.setLength(linklen);

            dest.append("<a ");
            if (linkTarget!=null) {
                dest.append("target=\"");
                dest.append(encodeHTML(linkTarget));
                dest.append("\" ");
            }
            dest.append("href=\"");
            String linkstr=link.toString();
            if (linkstr.startsWith("doi:") || linkstr.startsWith("hdl:")) {
                dest.append("http://dx.doi.org/");
                dest.append(encodeHTML(linkstr.substring(4)));
                dest.append("\">");
                dest.append(encodeHTML(linkstr));
            } else if (linkstr.startsWith("link:")) {
                dest.append(encodeHTML(linkstr.substring(5)));
                dest.append("\">");
                dest.append("local link");
            } else if (linkstr.startsWith("mailto:")) {
                dest.append(encodeHTML(linkstr));
                dest.append("\">");
                dest.append(encodeHTML(linkstr.substring(7)));
            } else {
                dest.append(encodeHTML(linkstr));
                dest.append("\">");
                dest.append("external link");
            }
            dest.append("</a>");

            p=p2;
        }
        dest.append(encodeHTML(s.substring(p)));
        return dest.toString();
    }

}

