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
import java.util.*;
import java.util.zip.*;

public class FileOpener implements Runnable
{
    private FileOpener() {}

    public static InputStream openFileInputStream(String name) throws IOException
    {
        return openFileInputStream(new File(name));
    }

    public static InputStream openFileInputStream(File file) throws IOException
    {
        // try to open file normally
        FileNotFoundException ex=null;
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            ex=e;
        }

        // try zipfile in parent dirs
        File akt=file;
        while ((akt=akt.getParentFile())!=null && !akt.isDirectory()) {
            if (akt.isFile()) synchronized(instance) {
                // try to Open as Zip File
                ZipFile zf=instance.openZip(akt);
                // try cache
                ZipEntry entry;
                if ((entry=(ZipEntry)instance.availZipEntries.get(file))==null) {
                    // fetch entry
                    String s=file.getPath().substring(akt.getPath().length()+1);
                    s=s.replace(File.separatorChar,'/');
                    entry=zf.getEntry(s);
                    if (entry==null) {
                        s=s.replace('/','\\');
                        entry=zf.getEntry(s);
                    }
                    if (entry==null) throw new FileNotFoundException("Cannot find file '"+s+"' in ZIP archive '"+akt.getPath()+"'!");
                    else {
                        instance.availZipEntryList.addElement(file);
                        instance.availZipEntries.put(file,entry);
                    }

                }
                return zf.getInputStream(entry);
            }
        }
        throw ex;
    }

    public static boolean exists(File file) {
        if (file.exists()) return true;
        else {
            ZipEntry entry=instance.getZipEntry(file);
            return (entry!=null && !entry.isDirectory());
        }
    }

    public static long getLastModified(File file) {
        if (file.exists()) return file.lastModified();
        else {
            ZipEntry entry=instance.getZipEntry(file);
            if (entry!=null) return entry.getTime();
            else return 0L;
        }
    }

    public static long getSize(File file) {
        if (file.exists()) return file.length();
        else {
            ZipEntry entry=instance.getZipEntry(file);
            if (entry!=null) return entry.getSize();
            else return 0L;
        }
    }

    /* cache */

    private ZipEntry getZipEntry(File file) {
        ZipEntry entry;

        // try cache
        if ((entry=(ZipEntry)instance.availZipEntries.get(file))!=null) return entry;

        // try zipfile in parent dirs
        File akt=file;
        while ((akt=akt.getParentFile())!=null && !akt.isDirectory()) {
            if (akt.isFile()) synchronized(this) {
                // try to Open as Zip File
                try {
                    ZipFile zf=openZip(akt);
                    String s=file.getPath().substring(akt.getPath().length()+1);
                    s=s.replace(File.separatorChar,'/');
                    entry=zf.getEntry(s);
                    if (entry==null) {
                        s=s.replace('/','\\');
                        entry=zf.getEntry(s);
                    }
                    if (entry!=null) {
                        availZipEntryList.addElement(file);
                        availZipEntries.put(file,entry);
                    }
                    return entry;
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public void run() {
        for(;;) {
            synchronized(this) {
                try {
                    while (availZipList.size()>=MAX_ZIP_CACHE) {
                        Object key=availZipList.elementAt(0);
                        ZipFile zf=(ZipFile)availZips.get(key);
                        zf.close();
                        availZips.remove(key);
                        availZipList.removeElementAt(0);
                    }
                    while (availZipEntryList.size()>=MAX_ENTRY_CACHE) {
                        Object key=availZipEntryList.elementAt(0);
                        ZipEntry entry=(ZipEntry)availZipEntries.get(key);
                        availZipEntries.remove(key);
                        availZipEntryList.removeElementAt(0);
                    }
                } catch (IOException io) {}
            }
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException ie) {}
        }
    }

    private synchronized ZipFile openZip(File zip) throws IOException {
        ZipFile zf;
        if ((zf=(ZipFile)availZips.get(zip))!=null) return zf;
        else {
            zf=new ZipFile(zip);
            availZipList.addElement(zip);
            availZips.put(zip,zf);
            return zf;
        }
    }
    /* static things for ZIP cache */
    private static final int MAX_ZIP_CACHE=5;
    private static final int MAX_ENTRY_CACHE=5000;
    private static final long SLEEP_TIME=1*60*1000L;
    private Hashtable availZips=new Hashtable();
    private Vector availZipList=new Vector();
    private Hashtable availZipEntries=new Hashtable();
    private Vector availZipEntryList=new Vector();

    private static FileOpener instance=new FileOpener();
    private static Thread cleaner=new Thread(instance);
    static {
        cleaner.setDaemon(true);
        cleaner.setPriority(Thread.MIN_PRIORITY);
        cleaner.start();
    }
}

