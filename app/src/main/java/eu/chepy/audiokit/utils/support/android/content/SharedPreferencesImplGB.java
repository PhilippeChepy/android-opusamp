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

package eu.chepy.audiokit.utils.support.android.content;

import android.content.SharedPreferences;

import java.util.Set;

import eu.chepy.audiokit.utils.support.android.utils.SharedPreferencesJsonStringSetWrapperUtils;

// Implementation for Gingerbread
/**
 * @hide
 */
final class SharedPreferencesImplGB extends SharedPreferencesImpl {
    // private static final String TAG = "SharedPreferenceCompatImplGB";

    @Override
    public Set<String> getStringSet(SharedPreferences prefs, String key, Set<String> defValues) {
        return SharedPreferencesJsonStringSetWrapperUtils.getStringSet(prefs, key, defValues);
    }
}