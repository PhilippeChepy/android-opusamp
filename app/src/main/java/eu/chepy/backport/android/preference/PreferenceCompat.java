/*
 * Copyright (C) 2012 Haruki Hasegawa
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

package eu.chepy.backport.android.preference;

import android.os.Build;
import android.preference.PreferenceManager;

import java.util.Set;


public final class PreferenceCompat {
    // private static final String TAG = "PreferenceCompat";

    private static final PreferenceImpl IMPL;

    static {
        final int version = Build.VERSION.SDK_INT;
        if (version >= 11/* Build.VERSION_CODES.HONEYCOMB */) {
            IMPL = new PreferenceImplHoneycomb();
        } else {
            IMPL = new PreferenceImplGB();
        }
    }

    private PreferenceCompat() {
        // hide constructor
    }

    public static boolean persistStringSet(android.preference.Preference pref, Set<String> values) {
        return IMPL.persistStringSet(pref, values);
    }

    /**
     * Attempts to get a persisted set of Strings from the
     * {@link android.content.SharedPreferences}.
     * <p>
     * This will check if this Preference is persistent, get the
     * SharedPreferences from the {@link PreferenceManager}, and get the value.
     * 
     * @param pref Preference to attempts to get a persisted set of Strings.
     * @param defaultReturnValue The default value to return if either the
     *            Preference is not persistent or the Preference is not in the
     *            shared preferences.
     * @return The value from the SharedPreferences or the default return value.
     */
    public static Set<String> getPersistedStringSet(android.preference.Preference pref, Set<String> defaultReturnValue) {
        return IMPL.getPersistedStringSet(pref, defaultReturnValue);
    }
}