/*
 * SettingsApplicationActivity.java
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
package net.opusapp.player.ui.activities.settings;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import net.opusapp.player.R;
import net.opusapp.player.licensing.BuildSpecific;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.ColorSchemeDialog;
import net.opusapp.player.ui.views.colorpicker.ColorPickerPreference;
import net.opusapp.player.utils.jni.JniMediaLib;

public class ApplicationSettingsActivity extends PreferenceActivity {

	public static final String TAG = ApplicationSettingsActivity.class.getSimpleName();



	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		addPreferencesFromResource(R.xml.preferences);

        setDatabaseOptimizationListener();

        setOnlineHelpListener();
		setOpenSourceLicensesListener();

        int embeddedArtCacheSize = PlayerApplication.getIntPreference(R.string.preference_key_embedded_art_cache_size, 100);

        final ColorPickerPreference primaryColorPreference = (ColorPickerPreference) findPreference(getString(R.string.preference_key_primary_color));
        primaryColorPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PlayerApplication.uiColorsChanged = true;
                return true;
            }
        });

        final ColorPickerPreference accentColorPreference = (ColorPickerPreference) findPreference(getString(R.string.preference_key_accent_color));
        accentColorPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PlayerApplication.uiColorsChanged = true;
                return true;
            }
        });

        final ColorPickerPreference foregroundColorPreference = (ColorPickerPreference) findPreference(getString(R.string.preference_key_foreground_color));
        foregroundColorPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PlayerApplication.uiColorsChanged = true;
                return true;
            }
        });

        final CheckBoxPreference useDarkIconsPreference = (CheckBoxPreference) findPreference(getString(R.string.preference_key_toolbar_dark_icons));
        useDarkIconsPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PlayerApplication.uiColorsChanged = true;
                return true;
            }
        });

        final Preference themePresetsPref = findPreference(getString(R.string.preference_key_color_presets));
        themePresetsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final ColorSchemeDialog colorSchemeDialog = new ColorSchemeDialog(ApplicationSettingsActivity.this);
                colorSchemeDialog.setPreferences(primaryColorPreference, accentColorPreference, foregroundColorPreference, useDarkIconsPreference);
                colorSchemeDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        ((ColorSchemeDialog) dialog).setPreferences(null, null, null, null);
                    }
                });
                colorSchemeDialog.show();
                return true;
            }
        });

        final Preference embeddedArtCacheSizePref = findPreference(getString(R.string.preference_key_embedded_art_cache_size));
        embeddedArtCacheSizePref.setSummary(String.format(getString(R.string.unit_MB), embeddedArtCacheSize));
        embeddedArtCacheSizePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                embeddedArtCacheSizePref.setSummary(String.format(getString(R.string.unit_MB), newValue));
                JniMediaLib.embeddedCoverCleanCache();
                return true;
            }
        });

        final Preference buyPremiumPreference = findPreference(getString(R.string.preference_key_premium));
        BuildSpecific.managePremiumPreference(this, buyPremiumPreference);
	}

    @SuppressWarnings("deprecation")
    private void setDatabaseOptimizationListener() {
        final Preference databaseOptimization = findPreference(getString(R.string.preference_key_optimize_database));
        databaseOptimization.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final ProgressDialog progressDialog = new ProgressDialog(ApplicationSettingsActivity.this);

                final AsyncTask<Void, Integer, Void> optimizationTask = new AsyncTask<Void, Integer, Void>() {

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        progressDialog.setTitle(R.string.preference_dialog_title_database_optimization);
                        progressDialog.setIndeterminate(true);
                        progressDialog.show();
                    }

                    @Override
                    protected Void doInBackground(Void... params) {
                        publishProgress(0);
                        PlayerApplication.getDatabaseOpenHelper().getWritableDatabase().rawQuery("VACUUM;", null);

                        for (int index = 0 ; index < PlayerApplication.mediaManagers.length ; index++) {
                            publishProgress(index + 1);
                            PlayerApplication.mediaManagers[index].getProvider().databaseMaintain();
                        }

                        return null;
                    }

                    @Override
                    protected void onProgressUpdate(Integer... values) {
                        super.onProgressUpdate(values);

                        if (values[0] == 0) {
                            progressDialog.setMessage(getString(R.string.progress_dialog_label_global_database));
                        }
                        else {
                            progressDialog.setMessage(String.format(getString(R.string.progress_dialog_label_current_database), values[0], PlayerApplication.mediaManagers.length));
                        }
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);
                        progressDialog.dismiss();
                    }
                };

                optimizationTask.execute();

                return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void setOnlineHelpListener() {
        final Preference onlineHelp = findPreference(getString(R.string.preference_key_privacy_policy));
        onlineHelp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://opusamp.com/privacy"));
                startActivity(browserIntent);
                return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void setOpenSourceLicensesListener() {
        final Preference openSourceLicenses = findPreference(getString(R.string.preference_key_opensource));
        openSourceLicenses.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                PlayerApplication.showOpenSourceDialog(ApplicationSettingsActivity.this).show();
                return true;
            }
        });
    }
}
