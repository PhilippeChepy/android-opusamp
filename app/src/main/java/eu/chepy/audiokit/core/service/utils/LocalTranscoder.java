/*
 * LocalTranscoder.java
 *
 * Copyright (c) 2012, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */

package eu.chepy.audiokit.core.service.utils;

import android.util.Log;

import eu.chepy.audiokit.utils.LogUtils;
import eu.chepy.audiokit.utils.jni.JniMediaLib;

public class LocalTranscoder extends JniMediaLib {

    private long streamHandle = 0;

    public LocalTranscoder() {
        if (engineInitialize(true) != 0) {
            LogUtils.LOGE(TAG, "unable to initialize engine");
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (streamHandle != 0) {
            streamFinalize(streamHandle);
        }

        engineFinalize();
        super.finalize();
    }

    public void load(String uri) {
        streamHandle = streamInitialize(uri);
    }

    public void start() {
        if (streamHandle != 0) {
            streamStart(streamHandle);
        }
    }

    public void stop() {
        if (streamHandle != 0) {
            streamStop(streamHandle);
        }
    }

    @Override
    protected void playbackEndNotification() {
        Log.w(TAG, "ts=end");
    }

    @Override
    protected void playbackUpdateTimestamp(long timestamp) {
        //Log.w(TAG, "ts="+timestamp);
    }
}
