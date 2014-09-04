/*
 * SettingsActivity.java
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
package eu.chepy.opus.player.core.service.providers.local.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;
import eu.chepy.opus.player.ui.utils.PlayerApplication;

public class SettingsActivity extends PreferenceActivity {

    private int providerId;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        providerId = 0;

        if (intent != null) {
            providerId = intent.getIntExtra(AbstractMediaManager.Provider.KEY_PROVIDER_ID, 0);
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        final PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName("provider-" + providerId);
        preferenceManager.setSharedPreferencesMode(MODE_PRIVATE);

        addPreferencesFromResource(R.xml.local_preferences);

        setLocationHandler();
        setGenreHandler();


        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + providerId, Context.MODE_PRIVATE);

        boolean fileDetails = sharedPrefs.getBoolean(getString(R.string.preference_key_storage_display_details), false);

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(resources.getString(R.string.preference_key_storage_display_details), fileDetails);
        editor.apply();

        final Preference genreDetails = findPreference(getString(R.string.preference_key_genre_display));
        genreDetails.setSummary(sharedPrefs.getString(getString(R.string.preference_key_genre_display), getString(R.string.preference_list_value_genre_show_albums)));
    }

    @SuppressWarnings("deprecation")
    private void setLocationHandler() {
        final Preference openSourceLicenses = findPreference(getString(R.string.preference_key_settings_location));
        openSourceLicenses.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final AbstractMediaManager mediaManager =
                        PlayerApplication.mediaManagers[PlayerApplication.getManagerIndex(providerId)];

                final AbstractMediaManager.Provider provider = mediaManager.getProvider();
                provider.getAbstractProviderAction(0).launch(SettingsActivity.this);
                return true;
            }
        });
    }

    private void setGenreHandler() {
        final Preference genreDetails = findPreference(getString(R.string.preference_key_genre_display));
        genreDetails.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                genreDetails.setSummary((String)newValue);
                return true;
            }
        });
    }
}
