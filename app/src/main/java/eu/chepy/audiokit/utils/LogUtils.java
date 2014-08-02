/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
