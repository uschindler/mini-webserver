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

public class MyStringTokenizer extends Object
{
    public MyStringTokenizer(String s, char delimiter)
    {
        this.s=s;
        this.delimiter=delimiter;
    }

    public String nextToken()
    {
        if (pos<0) return "";
        int pos1=s.indexOf(delimiter,pos);
        String s1;
        if (pos1>=0) {
            s1=s.substring(pos,pos1);
            pos=pos1+1;
        } else {
            s1=s.substring(pos);pos=-1;
        }
        return s1;
    }

    public boolean hasMoreTokens()
    {
        return (pos>=0);
    }

    // add your data members here
    private String s;
    private char delimiter;
    private int pos=0;
}

