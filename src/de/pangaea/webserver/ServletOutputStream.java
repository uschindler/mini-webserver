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

public class ServletOutputStream extends javax.servlet.ServletOutputStream
{

    protected ServletOutputStream(OutputStream out, HttpConnection parent) {
        this.out=out;
        this.parent=parent;
    }

    protected synchronized void flushInternal() throws IOException {
        if (buffer!=null) {
            parent.writeHeaders(out);
            parent.committed=true;
            buffer.writeTo(out);
            bytesWritten+=buffer.size();
            buffer=null;
        }
        out.flush();
    }

    public void flush() throws IOException {
        //flush(); no flushing by client of out allowed, only by HttpServletResponse.flushBuffer();
    }

    public void close() throws IOException {
        flushInternal();
        // out.close(); is closed by server
    }

    public void write(int i) throws IOException {
        if (doOutput) {
            if (buffer!=null && buffer.size()+1>bufSize) flushInternal();
            if (buffer!=null)
                buffer.write(i);
            else {
                out.write(i);
                bytesWritten++;
            }
        }
        contentLength++;
    }

    public void write(byte abyte0[], int off, int len) throws IOException {
        if (doOutput) {
            if (buffer!=null && buffer.size()+len>bufSize) flushInternal();
            if (buffer!=null)
                buffer.write(abyte0,off,len);
            else {
                out.write(abyte0,off,len);
                bytesWritten+=len;
            }
        }
        contentLength+=len;
    }

    protected void setBufSize(int bufSize) {
        this.bufSize=bufSize;
    }

    protected int getBufSize() {
        return bufSize;
    }

    protected void reset() {
        if (buffer!=null) {
            buffer.reset();
            contentLength=0;
        }
    }

    protected int getContentLength() {
        return contentLength;
    }

    protected int getBytesWritten() {
        return bytesWritten;
    }

    // disable output for HEAD request, but still count contentLength
    protected void disableOutput() {
        doOutput=false;
    }

    protected OutputStream out;
    private int bufSize=16932; // buffer until commit
    private ByteArrayOutputStream buffer=new ByteArrayOutputStream(16932);
    private int contentLength=0,bytesWritten=0;
    private boolean doOutput=true;
    private HttpConnection parent;

}
