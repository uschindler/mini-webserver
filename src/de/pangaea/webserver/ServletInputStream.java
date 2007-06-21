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

import java.io.*;

public class ServletInputStream extends javax.servlet.ServletInputStream {

    protected ServletInputStream(InputStream in, int size)
    {
        this.in=in;
        this.size=size;
    }

    public int available()
        throws IOException
    {
        int anz=in.available();
        return (size<0 || count+anz<=size)?anz:(size-count);
    }

    public void close() throws IOException
    {
        if (size>=0) in.skip(size-count);
        //in.close(); do not do this, stream is closed by handler of connection
    }

    public int read()
        throws IOException
    {
        if (size<0 || count<size) {
            int i = in.read();
            if(i != -1) count++;
            return i;
        } else return -1;
    }

    public int read(byte abyte0[], int i, int anz)
        throws IOException
    {
        if (size>=0 && count+anz>size) {
            anz=size-count;
        }
        if (anz==0) throw new EOFException();
        int k = in.read(abyte0, i, anz);
        if(k > 0) count+=k;
        if (k==0) throw new EOFException();
        return k;
    }

    protected InputStream in;
    private int size;
    private int count=0;
}
