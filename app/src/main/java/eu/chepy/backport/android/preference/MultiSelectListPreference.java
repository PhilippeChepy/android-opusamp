/*
 * Copyright (C) 2010 The Android Open Source Project
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

/*
 * Modified by Haruki Hasegawa
 * 
 * Original code is MultiSelectListPreference.java. 
 * Copied from Jelly Bean implementation.
 */

package eu.chepy.backport.android.preference;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;

import java.util.HashSet;
import java.util.Set;

import eu.chepy.audiokit.R;

/**
 * A {@link PreferenceCompat} that displays a list of entries as a dialog.
 * <p>
 * This preference will store a set of strings into the SharedPreferences. This
 * set will contain one or more values from the
 * {@link #setEntryValues(CharSequence[])} array.
 * 
 * @attr ref android.R.styleable#MultiSelectListPreference_entries
 * @attr ref android.R.styleable#MultiSelectListPreference_entryValues
 */
public class MultiSelectListPreference extends DialogPreference {
    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private Set<String> mValues = new HashSet<String>();
    private Set<String> mNewValues;
    private boolean mPreferenceChanged;

    public MultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MultiSelectListPreference);

        final int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            final int index = a.getIndex(i);
            switch (index) {
                case R.styleable.MultiSelectListPreference_android_entries:
                    mEntries = a.getTextArray(index);
                    break;
                case R.styleable.MultiSelectListPreference_android_entryValues:
                    mEntryValues = a.getTextArray(index);
                    break;
                default:
                    break;
            }
        }
        a.recycle();
    }

    public MultiSelectListPreference(Context context) {
        this(context, null);
    }

    /**
     * Sets the human-readable entries to be shown in the list. This will be
     * shown in subsequent dialogs.
     * <p>
     * Each entry must have a corresponding index in
     * {@link #setEntryValues(CharSequence[])}.
     * 
     * @param entries The entries.
     * @see #setEntryValues(CharSequence[])
     */
    public void setEntries(CharSequence[] entries) {
        mEntries = entries;
    }

    /**
     * @see #setEntries(CharSequence[])
     * @param entriesResId The entries array as a resource.
     */
    public void setEntries(int entriesResId) {
        setEntries(getContext().getResources().getTextArray(entriesResId));
    }

    /**
     * The list of entries to be shown in the list in subsequent dialogs.
     * 
     * @return The list as an array.
     */
    public CharSequence[] getEntries() {
        return mEntries;
    }

    /**
     * The array to find the value to save for a preference when an entry from
     * entries is selected. If a user clicks on the second item in entries, the
     * second item in this array will be saved to the preference.
     * 
     * @param entryValues The array to be used as values to save for the
     *            preference.
     */
    public void setEntryValues(CharSequence[] entryValues) {
        mEntryValues = entryValues;
    }

    /**
     * @see #setEntryValues(CharSequence[])
     * @param entryValuesResId The entry values array as a resource.
     */
    public void setEntryValues(int entryValuesResId) {
        setEntryValues(getContext().getResources().getTextArray(
                entryValuesResId));
    }

    /**
     * Returns the array of values to be saved for the preference.
     * 
     * @return The array of values.
     */
    public CharSequence[] getEntryValues() {
        return mEntryValues;
    }

    /**
     * Sets the value of the key. This should contain entries in
     * {@link #getEntryValues()}.
     * 
     * @param values The values to set for the key.
     */
    public void setValues(Set<String> values) {
        mValues.clear();
        mValues.addAll(values);

        // we shouldn't re-use the hash set, because 
        // persistStringSet() method does not copy the passed
        // arguments.
        final HashSet<String> clonedValues = new HashSet<String>(values);
        persistStringSetCompat(clonedValues);
    }

    /**
     * Retrieves the current value of the key.
     */
    public Set<String> getValues() {
        return mValues;
    }

    /**
     * Returns the index of the given value (in the entry values array).
     * 
     * @param value The value whose index should be returned.
     * @return The index of the value, or -1 if not found.
     */
    public int findIndexOfValue(String value) {
        if (value != null && mEntryValues != null) {
            for (int i = mEntryValues.length - 1; i >= 0; i--) {
                if (mEntryValues[i].equals(value)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);

        if (mEntries == null || mEntryValues == null) {
            throw new IllegalStateException(
                    "MultiSelectListPreference requires an entries array and "
                            + "an entryValues array.");
        }

        if (mNewValues == null) {
            mNewValues = new HashSet<String>();
            mNewValues.addAll(mValues);
            mPreferenceChanged = false;
        }
        
        final boolean[] checkedItems = getSelectedItems(mNewValues);
        builder.setMultiChoiceItems(mEntries, checkedItems,
                new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface dialog, int which,
                            boolean isChecked) {
                        if (isChecked) {
                            mPreferenceChanged |= mNewValues
                                    .add(mEntryValues[which].toString());
                        } else {
                            mPreferenceChanged |= mNewValues
                                    .remove(mEntryValues[which].toString());
                        }
                    }
                });
    }

    private boolean[] getSelectedItems(final Set<String> values) {
        final CharSequence[] entries = mEntryValues;
        final int entryCount = entries.length;
        boolean[] result = new boolean[entryCount];

        for (int i = 0; i < entryCount; i++) {
            result[i] = values.contains(entries[i].toString());
        }

        return result;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult && mPreferenceChanged) {
            final Set<String> values = mNewValues;
            if (callChangeListener(values)) {
                setValues(values);
            }
        }
        mNewValues = null;
        mPreferenceChanged = false;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        final CharSequence[] defaultValues = a.getTextArray(index);
        final int valueCount = defaultValues.length;
        final Set<String> result = new HashSet<String>();

        for (int i = 0; i < valueCount; i++) {
            result.add(defaultValues[i].toString());
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValues(restoreValue ? getPersistedStringSetCompat(mValues)
                : (Set<String>) defaultValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState myState = new SavedState(superState);
        myState.values = mValues;
        myState.newValues = mNewValues;
        myState.preferenceChanged = mPreferenceChanged;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            final SavedState myState = (SavedState) state;
            if (myState.values != null) {
                mValues =  myState.values;
            }
            if (myState.newValues != null) {
                mNewValues = myState.newValues;
            }
            mPreferenceChanged = myState.preferenceChanged;
            
            super.onRestoreInstanceState(myState.getSuperState());
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    private static class SavedState extends BaseSavedState {
        public Set<String> values;
        public Set<String> newValues;
        public boolean preferenceChanged;

        public SavedState(Parcel source) {
            super(source);
            values = readStringSet(source);
            newValues = readStringSet(source);
            preferenceChanged = readBoolean(source);
        }
        
        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            
            writeStringSet(dest, values);
            writeStringSet(dest, newValues);
            writeBoolean(dest, preferenceChanged);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
        
        private static Set<String> readStringSet(Parcel source) {
            final int n = source.readInt();
            final String[] strings = new String[n];
            final Set<String> values = new HashSet<String>(n);

            source.readStringArray(strings);

            final int stringCount = strings.length;
            for (int i = 0; i < stringCount; i++) {
                values.add(strings[i]);
            }
            
            return values;
        }
        
        private static void writeStringSet(Parcel dest, Set<String> values) {
            final int n = (values == null) ? 0 : values.size();
            final String[] arrayValues = new String[n];
            
            if (values != null) {
                values.toArray(arrayValues);
            }

            dest.writeInt(n);
            dest.writeStringArray(arrayValues);
        }
        
        private static boolean readBoolean(Parcel source) {
            return source.readInt() != 0;
        }
        
        private static void writeBoolean(Parcel dest, boolean value) {
            dest.writeInt((value) ? 1 : 0);
        }
    }

    protected boolean persistStringSetCompat(Set<String> values) {
        return PreferenceCompat.persistStringSet(this, values);
    }

    protected Set<String> getPersistedStringSetCompat(Set<String> defaultReturnValue) {
        return PreferenceCompat.getPersistedStringSet(this, defaultReturnValue);
    }
}