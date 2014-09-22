/*
 * CoverInputStream.java
 *
 * Copyright (c) 2014, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package net.opusapp.player.core.service.providers.local.utils;

import net.opusapp.player.utils.jni.JniMediaLib;

import java.io.IOException;
import java.io.InputStream;

public class CoverInputStream extends InputStream {

    public static final String TAG = CoverInputStream.class.getSimpleName();



    protected int pos;

    protected int count;

    protected int mark = 0;

    protected long nativeContext;



    public CoverInputStream(String filePath) {
        nativeContext = JniMediaLib.coverInputStreamOpen(filePath);

        pos = 0;
        count = 0;

        if (nativeContext != 0) {
            count = JniMediaLib.coverInputStreamReadGetCount(nativeContext);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if (nativeContext != 0) {
            JniMediaLib.coverInputStreamClose(nativeContext);
        }
    }

    public synchronized int read() {
        return (pos < count) && (nativeContext != 0) ? (JniMediaLib.coverInputStreamReadSingle(nativeContext, pos++) & 0xff) : -1;
    }

    public synchronized int read(byte target[], int off, int len) {
        if (nativeContext == 0) {
            return 0;
        }

        else if (off < 0 || len < 0 || len > target.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (pos >= count) {
            return -1;
        }
        if (pos + len > count) {
            len = count - pos;
        }
        if (len <= 0) {
            return 0;
        }

        JniMediaLib.coverInputStreamReadArray(nativeContext, target, off, len, pos);

        pos += len;
        return len;
    }

    public synchronized long skip(long n) {
        if (nativeContext == 0) {
            return 0;
        }

        if (pos + n > count) {
            n = count - pos;
        }
        if (n < 0) {
            return 0;
        }

        pos += n;
        return n;
    }

    public synchronized int available() {
        if (nativeContext == 0) {
            return 0;
        }

        return count - pos;
    }

    public boolean markSupported() {
        return true;
    }

    public void mark(int readAheadLimit) {
        mark = pos;
    }

    public synchronized void reset() {
        pos = mark;

        if (nativeContext == 0) {
            return;
        }
    }

    public void close() throws IOException {
    }

}
