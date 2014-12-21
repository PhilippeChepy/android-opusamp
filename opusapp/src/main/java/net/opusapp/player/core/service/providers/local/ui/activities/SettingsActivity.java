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
package net.opusapp.player.core.service.providers.local.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.MediaManager;
import net.opusapp.player.core.service.providers.local.LocalProvider;
import net.opusapp.player.ui.utils.PlayerApplication;

public class SettingsActivity extends PreferenceActivity {

    private int providerId;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        providerId = 0;

        if (intent != null) {
            providerId = intent.getIntExtra(MediaManager.Provider.KEY_PROVIDER_ID, 0);
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        final PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName("provider-" + providerId);
        preferenceManager.setSharedPreferencesMode(MODE_PRIVATE);

        addPreferencesFromResource(R.xml.local_preferences);

        setLocationHandler();
        setExtensionsHandler();
        setGenreHandler();


        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + providerId, Context.MODE_PRIVATE);

        boolean fileDetails = sharedPrefs.getBoolean(getString(R.string.preference_key_storage_display_details), false);
        boolean localArts = sharedPrefs.getBoolean(getString(R.string.preference_key_display_local_art), false);
        boolean emptyTags = sharedPrefs.getBoolean(getString(R.string.preference_key_display_source_if_no_tags), true);

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(resources.getString(R.string.preference_key_storage_display_details), fileDetails);
        editor.putBoolean(resources.getString(R.string.preference_key_display_local_art), localArts);
        editor.putBoolean(resources.getString(R.string.preference_key_display_source_if_no_tags), emptyTags);
        editor.apply();

        final Preference genreDetails = findPreference(getString(R.string.preference_key_genre_display));
        genreDetails.setSummary(sharedPrefs.getString(getString(R.string.preference_key_genre_display), getString(R.string.preference_list_value_genre_show_albums)));
    }

    @SuppressWarnings("deprecation")
    private void setLocationHandler() {
        final Preference locationPreference = findPreference(getString(R.string.preference_key_settings_location));
        locationPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final MediaManager mediaManager = PlayerApplication.mediaManager(providerId);
                final MediaManager.Provider provider = mediaManager.getProvider();

                if (provider instanceof LocalProvider) {
                    provider.getAction(LocalProvider.ACTION_INDEX_LOCATION).launch(SettingsActivity.this);
                }
                return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void setExtensionsHandler() {
        final Preference extensionPreference = findPreference(getString(R.string.preference_key_settings_extensions));
        extensionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final MediaManager mediaManager = PlayerApplication.mediaManager(providerId);
                final MediaManager.Provider provider = mediaManager.getProvider();

                if (provider instanceof LocalProvider) {
                    provider.getAction(LocalProvider.ACTION_INDEX_EXTENSIONS).launch(SettingsActivity.this);
                }
                return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
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
