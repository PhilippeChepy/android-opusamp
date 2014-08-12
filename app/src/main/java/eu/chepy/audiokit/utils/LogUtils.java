/*
 * LogUtils.java
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

package eu.chepy.audiokit.utils;

import android.util.Log;

/**
 * Helper methods that make logging more consistent throughout the app.
 */
public class LogUtils {

    private LogUtils() {
    }

    public static void LOGE(final String tag, final String msg) {
        Log.e(tag, msg);
    }

    public static void LOGW(final String tag, final String msg) {
        Log.w(tag, msg);
    }

    public static void LOGI(final String tag, final String msg) {
        Log.i(tag, msg);
    }

    public static void LOGD(final String tag, final String msg) {
        Log.d(tag, msg);
    }

    public static void LOGV(final String tag, final String msg) {
        Log.v(tag, msg);
    }

    public static void LOGException(final String tag, final String procedure, int index, final Exception exception) {
    	Log.w(tag, "Exception " + index + ":" + procedure + " : " + exception.getMessage());
    }
    
    public static void LOGService(final String tag, final String procedure, int index) {
    	Log.w(tag, "Not connected to service in " + index + ":" + procedure);
    }
}
