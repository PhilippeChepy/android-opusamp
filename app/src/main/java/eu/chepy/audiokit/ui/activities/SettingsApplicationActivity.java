/*
 * SettingsActivity.java
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
package eu.chepy.audiokit.ui.activities;

import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.ui.utils.PlayerApplication;

public class SettingsApplicationActivity extends PreferenceActivity {

	public static final String TAG = SettingsApplicationActivity.class.getSimpleName();



	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		addPreferencesFromResource(R.xml.preferences);
		
		setOpenSourceLicensesListener();
	}
	
    @SuppressWarnings("deprecation")
	private void setOpenSourceLicensesListener() {
        final Preference openSourceLicenses = findPreference("open_source");
        openSourceLicenses.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                PlayerApplication.showOpenSourceDialog(SettingsApplicationActivity.this).show();
                return true;
            }
        });
    }
}
