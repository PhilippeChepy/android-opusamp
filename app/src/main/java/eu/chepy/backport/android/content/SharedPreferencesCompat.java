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

package eu.chepy.backport.android.content;

import android.annotation.SuppressLint;
import android.content.SharedPreferences.Editor;
import android.os.Build;

import java.util.Set;


public final class SharedPreferencesCompat {
    // private static final String TAG = "SharedPreferencesCompat";

    private static final SharedPreferencesImpl IMPL;

    static {
        final int version = Build.VERSION.SDK_INT;
        if (version >= 11/* Build.VERSION_CODES.HONEYCOMB */) {
            IMPL = new SharedPreferencesImplHoneycomb();
        } else {
            IMPL = new SharedPreferencesImplGB();
        }
    }

    private SharedPreferencesCompat() {
        // hide constructor
    }

    /**
     * Retrieve a set of String values from the preferences.
     * 
     * @param prefs SharedPreferences to attempts to get a set of Strings.
     * @param key The name of the preference to retrieve.
     * @param defaultReturnValue Values to return if this preference does not exist.
     * @return Returns the preference values if they exist, or defValues. Throws
     *         ClassCastException if there is a preference with this name that
     *         is not a Set.
     * @throws ClassCastException
     */
    public static Set<String> getStringSet(
    		android.content.SharedPreferences prefs, String key, Set<String> defaultReturnValue) {
        return IMPL.getStringSet(prefs, key, defaultReturnValue);
    }

    public static class EditorCompat {
        // private static final String TAG =
        // "SharedPreferenceCompat.EditorCompat";

        private static final EditorImpl IMPL;

        private EditorCompat() {
            // hide constructor
        }

        static {
            final int version = Build.VERSION.SDK_INT;
            if (version >= 11/* Build.VERSION_CODES.HONEYCOMB */) {
                IMPL = new EditorImplHoneycomb();
            } else {
                IMPL = new EditorImplGB();
            }
        }

        /**
         * Set a set of String values in the preferences editor, to be written
         * back once commit() is called.
         * 
         * @param editor to attempts to put set of Strings.
         * @param key The name of the preference to modify.
         * @param values The new values for the preference.
         * @return Returns a reference to the same Editor object, so you can
         *         chain put calls together.
         */
        public static android.content.SharedPreferences.Editor putStringSet(
        		android.content.SharedPreferences.Editor editor, String key, Set<String> values) {

            return IMPL.putStringSet(editor, key, values);
        }

        @SuppressLint("NewApi")
		public static void tryApply(android.content.SharedPreferences.Editor editor) {
            try {
                editor.apply();
            } catch (AbstractMethodError unused) {
                // The app injected its own pre-Gingerbread
                // SharedPreferences.Editor implementation without
                // an apply method.
                editor.commit();
            }
        }
    }

    public static final class EditorCompatWrapper implements Editor {
        // private static final String TAG =
        // "SharedPreferenceCompat.EditorCompatWrapper";

        private final android.content.SharedPreferences.Editor mEditor;

        /**
         * @param editor Editor of attempts to operate
         */
        public EditorCompatWrapper(android.content.SharedPreferences.Editor editor) {
            mEditor = editor;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Editor putString(String key, String value) {
            mEditor.putString(key, value);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Editor putStringSet(String key, Set<String> values) {
            EditorCompat.putStringSet(mEditor, key, values);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Editor putInt(String key, int value) {
            mEditor.putInt(key, value);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Editor putLong(String key, long value) {
            mEditor.putLong(key, value);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Editor putFloat(String key, float value) {
            mEditor.putFloat(key, value);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Editor putBoolean(String key, boolean value) {
            mEditor.putBoolean(key, value);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Editor remove(String key) {
            mEditor.remove(key);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Editor clear() {
            mEditor.clear();
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void apply() {
            EditorCompat.tryApply(mEditor);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean commit() {
            return mEditor.commit();
        }
    }
}