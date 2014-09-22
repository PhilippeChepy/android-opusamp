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

package net.opusapp.player.utils.backport.android.preference;

import android.preference.Preference;

import java.util.Set;

abstract class PreferenceImpl {
    public abstract Set<String> getPersistedStringSet(Preference pref, Set<String> defaultReturnValue);
    public abstract boolean persistStringSet(Preference pref, Set<String> values);
}