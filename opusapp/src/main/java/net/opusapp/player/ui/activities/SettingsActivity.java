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
package net.opusapp.player.ui.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import net.opusapp.player.ui.utils.uil.NormalImageLoader;
import net.opusapp.player.ui.utils.uil.ThumbnailImageLoader;
import net.opusapp.player.ui.views.ColorSchemeDialog;
import net.opusapp.player.ui.views.colorpicker.ColorPickerPreference;

import java.io.File;
import java.text.DecimalFormat;

public class SettingsActivity extends PreferenceActivity {

	public static final String TAG = SettingsActivity.class.getSimpleName();



	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		addPreferencesFromResource(R.xml.preferences);

        setCacheCleanupListener();
        setDatabaseOptimizationListener();

        setOnlineHelpListener();
		setOpenSourceLicensesListener();

        final SharedPreferences sharedPrefs = getPreferences(Context.MODE_PRIVATE);

        int cacheSize = sharedPrefs.getInt(getString(R.string.preference_key_cache_size), 30);
        int thumbnailCacheSize = sharedPrefs.getInt(getString(R.string.preference_key_thumbnail_cache_size), 20);

/*
        boolean autoPlay = sharedPrefs.getBoolean(getString(R.string.preference_key_plug_auto_play), true);
        boolean autoPause = sharedPrefs.getBoolean(getString(R.string.preference_key_pause_call), true);

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(getString(R.string.preference_key_cache_size), cacheSize);
        editor.putInt(getString(R.string.preference_key_thumbnail_cache_size), thumbnailCacheSize);
        editor.putBoolean(getString(R.string.preference_key_plug_auto_play), autoPlay);
        editor.putBoolean(getString(R.string.preference_key_pause_call), autoPause);
        editor.apply();
*/

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
                final ColorSchemeDialog colorSchemeDialog = new ColorSchemeDialog(SettingsActivity.this);
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

        final Preference cacheSizePref = findPreference(getString(R.string.preference_key_cache_size));
        cacheSizePref.setSummary(String.format(getString(R.string.unit_MB), cacheSize));
        cacheSizePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                cacheSizePref.setSummary(String.format(getString(R.string.unit_MB), newValue));
                NormalImageLoader.init();
                return true;
            }
        });

        final Preference thumbCacheSizePref = findPreference(getString(R.string.preference_key_thumbnail_cache_size));
        thumbCacheSizePref.setSummary(String.format(getString(R.string.unit_MB), thumbnailCacheSize));
        thumbCacheSizePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                thumbCacheSizePref.setSummary(String.format(getString(R.string.unit_MB), newValue));
                ThumbnailImageLoader.init();
                return true;
            }
        });

        final Preference buyPremiumPreference = findPreference(getString(R.string.preference_key_premium));
        BuildSpecific.managePremiumPreference(this, buyPremiumPreference);
	}

    @SuppressWarnings("deprecation")
	private void setCacheCleanupListener() {
        final Preference cacheCleanup = findPreference(getString(R.string.preference_key_clear_cache));
        setDiscCacheSummary(cacheCleanup);
        cacheCleanup.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                NormalImageLoader.getInstance().clearDiskCache();
                ThumbnailImageLoader.getInstance().clearDiskCache();
                setDiscCacheSummary(cacheCleanup);
                return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void setDatabaseOptimizationListener() {
        final Preference databaseOptimization = findPreference(getString(R.string.preference_key_optimize_database));
        databaseOptimization.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final ProgressDialog progressDialog = new ProgressDialog(SettingsActivity.this);

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
                PlayerApplication.showOpenSourceDialog(SettingsActivity.this).show();
                return true;
            }
        });
    }

    private static long folderSize(File directory) {
        long length = 0;
        for (File file : directory.listFiles()) {
            if (file.isFile())
                length += file.length();
            else
                length += folderSize(file);
        }
        return length;
    }

    private void setDiscCacheSummary(Preference cacheCleanup) {
        File normalCacheDirectory = NormalImageLoader.getInstance().getDiskCache().getDirectory();
        File thumbnailCacheDirectory = ThumbnailImageLoader.getInstance().getDiskCache().getDirectory();

        final Double allocated = (double)(folderSize(normalCacheDirectory) + folderSize(thumbnailCacheDirectory))/ (double)(1048576);

        final DecimalFormat decimalFormat = new DecimalFormat();
        decimalFormat.setMaximumFractionDigits(2);
        decimalFormat.setMinimumFractionDigits(2);

        cacheCleanup.setSummary(String.format(getString(R.string.unit_MB), decimalFormat.format(allocated)));
    }
}
